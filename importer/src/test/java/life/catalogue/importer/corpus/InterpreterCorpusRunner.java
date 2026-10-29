package life.catalogue.importer.corpus;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.ParsedNameUsage;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.importer.coldp.ColdpInterpreter;
import life.catalogue.importer.dwca.DwcInterpreter;
import life.catalogue.interpreter.NameInterpreter;

import org.gbif.dwc.terms.DwcTerm;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLongArray;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/**
 * Runs the name interpretation of the importers over a corpus of verbatim records and writes one row of interpreted
 * name properties per record, in input order, so that two runs over the same input - before and after a change to the
 * parser or the interpreter - can be compared row by row with {@link InterpreterCorpusDiff}.
 * <p>
 * A record goes through exactly what an import does with its name: {@link DwcInterpreter#interpretName} for DwC and
 * {@link ColdpInterpreter#interpretName} for ColDP, with the name settings of its dataset and the format's default for
 * preferring atoms. An exception is recorded as the outcome of that row, not the end of the run.
 * See docs/INTERPRETER-CORPUS.md
 * <pre>
 * mvn -q -pl importer -am install -DskipTests
 * mvn -q -pl importer test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
 *   -Dexec.args="-Xmx4g --enable-native-access=ALL-UNNAMED -cp %classpath life.catalogue.importer.corpus.InterpreterCorpusRunner \
 *     /path/to/corpus.tsv.gz target/interpreter-corpus/baseline [--fraction 0.02] [--seed 42] [--threads 8] [--limit 1000]"
 * </pre>
 */
public class InterpreterCorpusRunner {
  static final String OUTPUT = "interpreted.tsv.gz";
  static final String META = "meta.txt";
  static final String OK = "OK";
  static final String NONE = "NONE";
  static final String ERROR = "ERROR";
  private static final int BATCH = 20_000;

  private record Key(DatasetSettings settings, boolean preferAtomsDefault) {}

  private final Map<Key, NameInterpreter> interpreters = new ConcurrentHashMap<>();
  // rows, weighted rows: OK, NONE, ERROR
  private final AtomicLongArray counts = new AtomicLongArray(6);

  private NameInterpreter interpreter(DatasetSettings settings, boolean preferAtomsDefault) {
    return interpreters.computeIfAbsent(new Key(settings, preferAtomsDefault), k -> new NameInterpreter(k.settings, k.preferAtomsDefault));
  }

  /**
   * @return the output row of one record, the values in the order of {@link Field}
   */
  Object[] interpret(CorpusRecord r) {
    Object[] row = new Object[Field.values().length];
    row[Field.ROW.ordinal()] = r.line();
    row[Field.N.ordinal()] = r.n();
    row[Field.TYPE.ordinal()] = r.type().prefixedName();
    row[Field.DATASET.ordinal()] = r.datasetKey();
    row[Field.INPUT.ordinal()] = r.input();
    VerbatimRecord v = r.toVerbatim();
    int status;
    try {
      Optional<ParsedNameUsage> pnu;
      if (r.type() == DwcTerm.Taxon) {
        pnu = DwcInterpreter.interpretName(interpreter(r.settings(), false), String.valueOf(r.line()), v);
      } else if (r.type() == ColdpTerm.Name || r.type() == ColdpTerm.NameUsage) {
        pnu = ColdpInterpreter.interpretName(interpreter(r.settings(), true), v);
      } else {
        throw new IllegalArgumentException("Unsupported row type " + r.type());
      }
      if (pnu.isPresent()) {
        status = 0;
        row[Field.STATUS.ordinal()] = OK;
        for (Field f : Field.values()) {
          if (f.isNameProperty()) {
            row[f.ordinal()] = f.get(pnu.get());
          }
        }
      } else {
        status = 1;
        row[Field.STATUS.ordinal()] = NONE;
      }
    } catch (Exception e) {
      status = 2;
      row[Field.STATUS.ordinal()] = ERROR;
      row[Field.ERROR.ordinal()] = e.getClass().getSimpleName() + ": " + e.getMessage();
    }
    row[Field.ISSUES.ordinal()] = Field.issues(v.getIssues());
    counts.incrementAndGet(status);
    counts.addAndGet(3 + status, r.n());
    return row;
  }

  /**
   * @param limit the maximum number of records to interpret, 0 for all
   * @return the summary written to meta.txt
   */
  public String run(File input, File outDir, double fraction, long seed, int threads, long limit) throws Exception {
    Instant start = Instant.now();
    // what runs is what was built when it started, edits made while it runs do not count
    String code = RunInfo.git();
    String parser = RunInfo.nameParser();
    ForkJoinPool pool = new ForkJoinPool(threads);
    long read = 0;
    CorpusReader.Kind kind;
    try (CorpusReader reader = new CorpusReader(input, fraction, seed);
         BufferedWriter w = CorpusIO.writer(new File(outDir, OUTPUT))
    ) {
      kind = reader.kind();
      w.write(Field.header());
      w.write('\n');
      List<CorpusRecord> batch = new ArrayList<>(BATCH);
      CorpusRecord r;
      while ((limit <= 0 || read < limit) && (r = reader.next()) != null) {
        read++;
        batch.add(r);
        if (batch.size() == BATCH) {
          flush(pool, batch, w);
          batch = new ArrayList<>(BATCH);
        }
      }
      flush(pool, batch, w);
    } finally {
      pool.shutdown();
    }
    String meta = meta(input, kind, fraction, seed, threads, limit, code, parser, Duration.between(start, Instant.now()));
    Files.writeString(new File(outDir, META).toPath(), meta, StandardCharsets.UTF_8);
    return meta;
  }

  private void flush(ForkJoinPool pool, List<CorpusRecord> batch, BufferedWriter w) throws Exception {
    if (batch.isEmpty()) return;
    // a parallel stream keeps the encounter order in toList, so the output follows the input
    List<Object[]> rows = pool.submit(() -> batch.parallelStream().map(this::interpret).toList()).get();
    for (Object[] row : rows) {
      CorpusIO.writeRow(w, row);
    }
  }

  private String meta(File input, CorpusReader.Kind kind, double fraction, long seed, int threads, long limit,
                      String code, String parser, Duration took) {
    StringBuilder sb = new StringBuilder();
    sb.append("input: ").append(input.getAbsolutePath()).append(" (").append(kind).append(")\n");
    sb.append("sample: fraction ").append(fraction).append(", seed ").append(seed);
    if (limit > 0) {
      sb.append(", limit ").append(limit);
    }
    sb.append('\n');
    sb.append("code: ").append(code).append('\n');
    sb.append("name parser: ").append(parser).append('\n');
    sb.append("interpreters: ").append(interpreters.size()).append(" setting combinations\n");
    sb.append(String.format("records: %,d interpreted (%,d weighted): %,d OK (%,d), %,d NONE (%,d), %,d ERROR (%,d)%n",
      counts.get(0) + counts.get(1) + counts.get(2), counts.get(3) + counts.get(4) + counts.get(5),
      counts.get(0), counts.get(3), counts.get(1), counts.get(4), counts.get(2), counts.get(5)));
    sb.append("took: ").append(took.toSeconds()).append("s with ").append(threads).append(" threads\n");
    return sb.toString();
  }

  /**
   * The interpreters log a line for every odd name, which at corpus scale is millions of lines.
   */
  static void quiet() {
    ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(Level.ERROR);
  }

  public static void main(String[] args) throws Exception {
    List<String> pos = new ArrayList<>();
    double fraction = 1;
    long seed = 42;
    int threads = Runtime.getRuntime().availableProcessors();
    long limit = 0;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--fraction" -> fraction = Double.parseDouble(args[++i]);
        case "--seed" -> seed = Long.parseLong(args[++i]);
        case "--threads" -> threads = Integer.parseInt(args[++i]);
        case "--limit" -> limit = Long.parseLong(args[++i]);
        default -> pos.add(args[i]);
      }
    }
    if (pos.size() != 2 || !new File(pos.get(0)).isFile()) {
      System.err.println("Usage: InterpreterCorpusRunner <corpus.tsv[.gz]> <outDir> [--fraction f] [--seed s] [--threads t] [--limit n]");
      System.exit(1);
    }
    quiet();
    System.out.print(new InterpreterCorpusRunner().run(new File(pos.get(0)), new File(pos.get(1)), fraction, seed, threads, limit));
  }
}
