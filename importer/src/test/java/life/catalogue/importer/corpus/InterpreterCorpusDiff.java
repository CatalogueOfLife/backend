package life.catalogue.importer.corpus;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

/**
 * Compares two runs of {@link InterpreterCorpusRunner} over the same input row by row and reports what changed,
 * weighted by the number of verbatim records behind each row:
 * <ul>
 *   <li>text fields - label, scientific name, authorship and the notes - by the {@link TextChange.Level} it takes to
 *   tell them apart, and real text changes by their likely {@link TextChange#cause cause}</li>
 *   <li>parsed properties and authorship atoms by whether they changed</li>
 *   <li>issues one by one, added or removed</li>
 * </ul>
 * It writes {@code report.txt} with the counts and a deterministic sample of examples for every kind of change, and
 * {@code changes.tsv.gz} with every changed field of every row.
 * <pre>
 * mvn -q -pl importer test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
 *   -Dexec.args="-cp %classpath life.catalogue.importer.corpus.InterpreterCorpusDiff \
 *     target/interpreter-corpus/baseline target/interpreter-corpus/phase1 target/interpreter-corpus/diff-phase1 [--examples 20]"
 * </pre>
 */
public class InterpreterCorpusDiff {
  static final String REPORT = "report.txt";
  static final String CHANGES = "changes.tsv.gz";
  private static final Field[] FIELDS = Field.values();
  private static final HashFunction HASH = Hashing.murmur3_32_fixed(42);

  private final int examples;
  // weighted and unweighted counts per key
  private final Map<String, long[]> counts = new TreeMap<>();
  private final Map<String, long[]> datasets = new HashMap<>();
  private final Map<String, PriorityQueue<Example>> samples = new TreeMap<>();
  private long rows;
  private long weight;
  private long changedRows;
  private long changedWeight;
  private long significantRows;
  private long significantWeight;
  private long unmatched;

  private record Example(int hash, String[] before, String[] after, Field field, String detail) {}

  public InterpreterCorpusDiff(int examples) {
    this.examples = examples;
  }

  private void count(String key, long n) {
    long[] c = counts.computeIfAbsent(key, k -> new long[2]);
    c[0]++;
    c[1] += n;
  }

  private void sample(String bucket, String[] before, String[] after, Field field, String detail) {
    int h = HASH.hashLong(Long.parseLong(after[Field.ROW.ordinal()])).asInt();
    // keep the examples with the smallest hashes: a stable, unbiased sample
    PriorityQueue<Example> q = samples.computeIfAbsent(bucket, k -> new PriorityQueue<>(Comparator.comparingInt(Example::hash).reversed()));
    if (q.size() < examples) {
      q.add(new Example(h, before, after, field, detail));
    } else if (q.peek().hash() > h) {
      q.poll();
      q.add(new Example(h, before, after, field, detail));
    }
  }

  /**
   * Compares the fields of one row in two runs.
   * @return the changes, one per changed field and per changed issue: field, level and cause
   */
  List<String[]> compare(String[] before, String[] after) {
    List<String[]> changes = new ArrayList<>();
    for (Field f : FIELDS) {
      String b = before[f.ordinal()];
      String a = after[f.ordinal()];
      switch (f.group) {
        case META -> {}
        case STATUS -> {
          if (!Objects.equals(b, a)) {
            changes.add(new String[]{f.name(), b + "->" + a, null, b, a});
          }
        }
        case TEXT, NOTES -> {
          TextChange.Level level = TextChange.level(b, a);
          if (level != TextChange.Level.SAME) {
            String cause = level == TextChange.Level.TEXT ? TextChange.cause(b, a) : null;
            changes.add(new String[]{f.name(), level.name(), cause, b, a});
          }
        }
        case STRUCTURE, AUTHOR_ATOMS -> {
          if (!Objects.equals(b, a)) {
            String level = b == null ? "ADDED" : a == null ? "REMOVED" : "CHANGED";
            changes.add(new String[]{f.name(), level, null, b, a});
          }
        }
        case ISSUES -> {
          Set<String> bi = issues(b);
          Set<String> ai = issues(a);
          for (String i : ai) {
            if (!bi.contains(i)) changes.add(new String[]{f.name(), "ADDED", i, null, i});
          }
          for (String i : bi) {
            if (!ai.contains(i)) changes.add(new String[]{f.name(), "REMOVED", i, i, null});
          }
        }
      }
    }
    return changes;
  }

  private static Set<String> issues(@Nullable String x) {
    return x == null ? Set.of() : new TreeSet<>(Arrays.asList(x.split(",")));
  }

  /**
   * @return true for a change that is more than a different spelling of the same text
   */
  static boolean isSignificant(String[] change) {
    Field f = Field.valueOf(change[0]);
    return switch (f.group) {
      case TEXT, NOTES -> TextChange.Level.valueOf(change[1]).isSignificant();
      case ISSUES -> false;
      default -> true;
    };
  }

  private void add(String[] before, String[] after, BufferedWriter changesOut) throws IOException {
    long n = Long.parseLong(after[Field.N.ordinal()]);
    rows++;
    weight += n;
    List<String[]> changes = compare(before, after);
    if (changes.isEmpty()) return;
    changedRows++;
    changedWeight += n;
    boolean significant = changes.stream().anyMatch(InterpreterCorpusDiff::isSignificant);
    if (significant) {
      significantRows++;
      significantWeight += n;
      String dataset = after[Field.DATASET.ordinal()];
      if (dataset != null) {
        long[] c = datasets.computeIfAbsent(dataset, k -> new long[2]);
        c[0]++;
        c[1] += n;
      }
      count("type " + after[Field.TYPE.ordinal()], n);
    }
    for (String[] c : changes) {
      Field f = Field.valueOf(c[0]);
      String key = f.name() + " " + c[1] + (c[2] == null ? "" : " " + c[2]);
      count(key, n);
      sample(key, before, after, f, c[2]);
      CorpusIO.writeRow(changesOut, after[Field.ROW.ordinal()], n, after[Field.TYPE.ordinal()], after[Field.DATASET.ordinal()],
        c[0], c[1], c[2], c[3], c[4], after[Field.INPUT.ordinal()]);
    }
  }

  public String diff(File beforeDir, File afterDir, File outDir) throws IOException {
    try (BufferedReader b = CorpusIO.reader(new File(beforeDir, InterpreterCorpusRunner.OUTPUT));
         BufferedReader a = CorpusIO.reader(new File(afterDir, InterpreterCorpusRunner.OUTPUT));
         BufferedWriter changesOut = CorpusIO.writer(new File(outDir, CHANGES))
    ) {
      checkHeader(b.readLine(), beforeDir);
      checkHeader(a.readLine(), afterDir);
      CorpusIO.writeRow(changesOut, "row", "n", "type", "dataset", "field", "level", "detail", "before", "after", "input");
      String[] br = next(b);
      String[] ar = next(a);
      while (br != null && ar != null) {
        long bl = Long.parseLong(br[Field.ROW.ordinal()]);
        long al = Long.parseLong(ar[Field.ROW.ordinal()]);
        if (bl == al) {
          add(br, ar, changesOut);
          br = next(b);
          ar = next(a);
        } else if (bl < al) {
          unmatched++;
          br = next(b);
        } else {
          unmatched++;
          ar = next(a);
        }
      }
      while (br != null) { unmatched++; br = next(b); }
      while (ar != null) { unmatched++; ar = next(a); }
    }
    String report = report(beforeDir, afterDir);
    Files.writeString(new File(outDir, REPORT).toPath(), report, StandardCharsets.UTF_8);
    return report;
  }

  private static void checkHeader(String header, File dir) {
    if (!Field.header().equals(header)) {
      throw new IllegalArgumentException("The run in " + dir + " was written with other columns, rerun it");
    }
  }

  @Nullable
  private static String[] next(BufferedReader r) throws IOException {
    String line = r.readLine();
    return line == null ? null : CorpusIO.split(line, FIELDS.length);
  }

  private static String meta(File dir) {
    try {
      return Files.readString(new File(dir, InterpreterCorpusRunner.META).toPath(), StandardCharsets.UTF_8).trim().indent(2);
    } catch (IOException e) {
      return "  no " + InterpreterCorpusRunner.META + "\n";
    }
  }

  private String report(File beforeDir, File afterDir) {
    StringBuilder sb = new StringBuilder();
    sb.append("before: ").append(beforeDir).append('\n').append(meta(beforeDir));
    sb.append("after: ").append(afterDir).append('\n').append(meta(afterDir));
    sb.append('\n');
    sb.append(String.format("rows compared: %,d (%,d records)%n", rows, weight));
    sb.append(String.format("rows changed: %,d (%,d records)%n", changedRows, changedWeight));
    sb.append(String.format("rows changed significantly: %,d (%,d records)%n", significantRows, significantWeight));
    sb.append("  significant: any change of a parsed property, an authorship atom or the status, or a text that differs\n");
    sb.append("  in more than whitespace, punctuation, case or diacritics. Issue changes alone are not significant.\n");
    if (unmatched > 0) {
      sb.append(String.format("rows in one run only: %,d - the runs did not read the same rows%n", unmatched));
    }

    sb.append("\n== Changes ==   rows   records   (field level [cause or issue])\n");
    counts.entrySet().stream()
      .sorted(Comparator.comparing((Map.Entry<String, long[]> e) -> !e.getKey().startsWith("type "))
        .thenComparing(e -> -e.getValue()[1]))
      .forEach(e -> sb.append(String.format("%,12d %,12d   %s%n", e.getValue()[0], e.getValue()[1], e.getKey())));

    if (!datasets.isEmpty()) {
      sb.append("\n== Datasets with the most significantly changed records ==\n");
      datasets.entrySet().stream()
        .sorted(Comparator.comparing((Map.Entry<String, long[]> e) -> -e.getValue()[1]))
        .limit(40)
        .forEach(e -> sb.append(String.format("%10s %,12d rows %,12d records%n", e.getKey(), e.getValue()[0], e.getValue()[1])));
    }

    sb.append("\n== Examples ==\n");
    samples.forEach((bucket, q) -> {
      sb.append("\n--- ").append(bucket).append('\n');
      q.stream().sorted(Comparator.comparingInt(Example::hash)).forEach(ex -> {
        Function<String[], String> show = r -> r[ex.field().ordinal()];
        sb.append("  row ").append(ex.after()[Field.ROW.ordinal()]);
        String ds = ex.after()[Field.DATASET.ordinal()];
        if (ds != null) {
          sb.append(" dataset ").append(ds);
        }
        sb.append(" x").append(ex.after()[Field.N.ordinal()]).append(": ").append(ex.after()[Field.INPUT.ordinal()]).append('\n');
        if (ex.field().group != Field.Group.ISSUES) {
          sb.append("    ").append(show.apply(ex.before())).append("\n -> ").append(show.apply(ex.after())).append('\n');
        }
        if (ex.field() != Field.LABEL && ex.field().group != Field.Group.STATUS) {
          sb.append("    label ").append(ex.before()[Field.LABEL.ordinal()]).append("\n       -> ").append(ex.after()[Field.LABEL.ordinal()]).append('\n');
        }
        if (ex.field().group == Field.Group.STATUS && ex.after()[Field.ERROR.ordinal()] != null) {
          sb.append("    ").append(ex.after()[Field.ERROR.ordinal()]).append('\n');
        }
      });
    });
    return sb.toString();
  }

  public static void main(String[] args) throws IOException {
    List<String> pos = new ArrayList<>();
    int examples = 20;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("--examples")) {
        examples = Integer.parseInt(args[++i]);
      } else {
        pos.add(args[i]);
      }
    }
    if (pos.size() != 3) {
      System.err.println("Usage: InterpreterCorpusDiff <before run dir> <after run dir> <out dir> [--examples n]");
      System.exit(1);
    }
    String report = new InterpreterCorpusDiff(examples).diff(new File(pos.get(0)), new File(pos.get(1)), new File(pos.get(2)));
    // the head of the report, the examples are in the file
    System.out.print(report.substring(0, Math.max(0, report.indexOf("\n== Examples =="))));
    System.out.println("\n" + new File(pos.get(2), REPORT).getAbsolutePath());
  }
}
