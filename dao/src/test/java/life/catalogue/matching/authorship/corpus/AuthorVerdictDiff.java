package life.catalogue.matching.authorship.corpus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Compares the verdicts of two report runs over the same pairs, which is how a change to the author comparison
 * is reviewed: every pair whose verdict flipped, grouped by what happened to it.
 *
 * Run it in a forked JVM, paths are relative to the dao module. See docs/AUTHOR-CORPUS.md
 * <pre>
 * mvn -q -pl dao test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
 *   -Dexec.args="-cp %classpath life.catalogue.matching.authorship.corpus.AuthorVerdictDiff \
 *     target/author-corpus/verdicts-before.tsv.gz target/author-corpus/verdicts.tsv.gz"
 * </pre>
 */
public class AuthorVerdictDiff {
  private static final int MAX_LISTED = 50;
  static final List<String> COLUMNS = List.of(
    "code", "keyA", "keyB", "label", "source", "weight", "verdict", "verdictNoYear", "authorshipA", "authorshipB"
  );

  public record VerdictRow(String code, String keyA, String keyB, String label, String source, int weight,
                           String verdict, String verdictNoYear, String authorshipA, String authorshipB) {
    String id() {
      return code + " " + keyA + " " + keyB;
    }

    String[] toRow() {
      return new String[]{code, keyA, keyB, label, source, String.valueOf(weight), verdict, verdictNoYear, authorshipA, authorshipB};
    }

    static VerdictRow of(String[] row) {
      return new VerdictRow(ExportRow.col(row, 0) == null ? "" : ExportRow.col(row, 0), row[1], row[2],
        ExportRow.col(row, 3), ExportRow.col(row, 4), Integer.parseInt(ExportRow.col(row, 5)),
        ExportRow.col(row, 6), ExportRow.col(row, 7), ExportRow.col(row, 8), ExportRow.col(row, 9));
    }
  }

  public record Flip(VerdictRow before, VerdictRow after) {
    /**
     * @return what the flip means for a pair of that label: fixed, regressed or merely changed
     */
    String kind() {
      String right = before.label.equals("SAME") ? "EQUAL" : before.label.equals("DIFF") ? "DIFFERENT" : null;
      if (after.verdict.equals(right)) return "fixed";
      return before.verdict.equals(right) ? "regressed" : "changed";
    }

    String transition() {
      return before.label + ": " + before.verdict + " -> " + after.verdict + " (" + kind() + ")";
    }
  }

  public record Diff(List<Flip> flips, int onlyBefore, int onlyAfter, int compared) {
    public long fixed() {
      return flips.stream().filter(f -> f.kind().equals("fixed")).count();
    }

    public long regressed() {
      return flips.stream().filter(f -> f.kind().equals("regressed")).count();
    }
  }

  public static List<VerdictRow> read(File verdicts) throws IOException {
    List<VerdictRow> rows = new ArrayList<>();
    CorpusIO.read(verdicts, COLUMNS, row -> rows.add(VerdictRow.of(row)));
    return rows;
  }

  public static Diff diff(List<VerdictRow> before, List<VerdictRow> after) {
    Map<String, VerdictRow> old = new LinkedHashMap<>();
    before.forEach(r -> old.put(r.id(), r));
    List<Flip> flips = new ArrayList<>();
    int onlyAfter = 0;
    int compared = 0;
    for (VerdictRow a : after) {
      VerdictRow b = old.remove(a.id());
      if (b == null) {
        onlyAfter++;
      } else {
        compared++;
        if (!b.verdict.equals(a.verdict)) {
          flips.add(new Flip(b, a));
        }
      }
    }
    flips.sort(Comparator.comparingInt((Flip f) -> f.after.weight).reversed().thenComparing(f -> f.after.id()));
    return new Diff(flips, old.size(), onlyAfter, compared);
  }

  public static String render(Diff d) {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("Compared %,d pairs: %,d verdicts changed, %,d fixed, %,d regressed.%n",
      d.compared, d.flips.size(), d.fixed(), d.regressed()));
    if (d.onlyBefore + d.onlyAfter > 0) {
      sb.append(String.format("Pairs in one run only: %,d before, %,d after - the runs did not read the same pairs.%n",
        d.onlyBefore, d.onlyAfter));
    }
    if (d.flips.isEmpty()) {
      return sb.append("No verdict changed.\n").toString();
    }
    Map<String, List<Flip>> byTransition = new TreeMap<>();
    d.flips.forEach(f -> byTransition.computeIfAbsent(f.transition(), k -> new ArrayList<>()).add(f));
    byTransition.forEach((transition, flips) -> {
      long names = flips.stream().mapToLong(f -> f.after.weight).sum();
      sb.append(String.format("%n## %s: %,d pairs backed by %,d names%n", transition, flips.size(), names));
      flips.stream().limit(MAX_LISTED).forEach(f -> sb.append(String.format("  %6d  %-11s %s  |  %s%n",
        f.after.weight, f.after.code, f.after.authorshipA, f.after.authorshipB)));
      if (flips.size() > MAX_LISTED) {
        sb.append(String.format("  ... and %,d more%n", flips.size() - MAX_LISTED));
      }
    });
    return sb.toString();
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 2 || !new File(args[0]).isFile() || !new File(args[1]).isFile()) {
      System.err.println("Usage: AuthorVerdictDiff <verdicts-before.tsv[.gz]> <verdicts-after.tsv[.gz]>");
      System.exit(1);
    }
    System.out.println(render(diff(read(new File(args[0])), read(new File(args[1])))));
  }
}
