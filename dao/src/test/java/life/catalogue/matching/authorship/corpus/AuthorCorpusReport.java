package life.catalogue.matching.authorship.corpus;

import life.catalogue.common.io.Resources;
import life.catalogue.common.io.TabWriter;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.matching.Equality;
import life.catalogue.matching.authorship.AuthorComparator;
import life.catalogue.matching.authorship.corpus.CorpusEvaluator.Verdict;
import life.catalogue.matching.authorship.corpus.LabelRules.Label;
import life.catalogue.matching.authorship.corpus.LabelRules.Source;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Runs the author comparison over a mined corpus and writes what it got right and wrong: confusion matrices,
 * the disagreements backed by most names as a worklist, and one verdict per pair for {@link AuthorVerdictDiff}.
 *
 * Run it in a forked JVM, paths are relative to the dao module. See docs/AUTHOR-CORPUS.md
 * <pre>
 * mvn -q -pl dao -am install -DskipTests    # -pl dao takes the api and its author map from ~/.m2, not from the checkout
 * mvn -q -pl dao test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
 *   -Dexec.args="-Xmx4g -cp %classpath life.catalogue.matching.authorship.corpus.AuthorCorpusReport \
 *     target/author-corpus/pairs.tsv.gz target/author-corpus"
 * </pre>
 */
public class AuthorCorpusReport {
  static final String REPORT = "report.txt";
  static final String VERDICTS = "verdicts.tsv.gz";
  private static final String AUTHOR_MAP = "authorship/authormap.txt";
  private static final int TOP = 200;
  private static final int MIN_ALIAS_WEIGHT = 10;
  private static final Comparator<Verdict> LIGHTEST_FIRST = Comparator.comparingInt((Verdict v) -> v.pair().weight())
                                                                     .thenComparing(v -> v.pair().id(), Comparator.reverseOrder());

  /**
   * The pairs of one kind backed by most names, without holding the rest.
   */
  private static class Top {
    final String title;
    final String explanation;
    final Predicate<Verdict> filter;
    final PriorityQueue<Verdict> queue = new PriorityQueue<>(LIGHTEST_FIRST);
    int pairs;
    long names;

    Top(String title, String explanation, Predicate<Verdict> filter) {
      this.title = title;
      this.explanation = explanation;
      this.filter = filter;
    }

    void offer(Verdict v) {
      if (filter.test(v)) {
        pairs++;
        names += v.pair().weight();
        queue.add(v);
        if (queue.size() > TOP) {
          queue.poll();
        }
      }
    }

    void render(StringBuilder sb) {
      sb.append(String.format("%n## %s%n%s%n%,d pairs backed by %,d names, the first %d:%n",
        title, explanation, pairs, names, Math.min(TOP, pairs)));
      List<Verdict> list = new ArrayList<>(queue);
      list.sort(LIGHTEST_FIRST.reversed());
      for (Verdict v : list) {
        AuthorPair p = v.pair();
        sb.append(String.format("  %6d  %-10s %-14s %-9s %s  |  %s      [%s, nidx %d, datasets %d/%d]%n",
          p.weight(), code(p), p.source(), v.verdict(),
          p.a().authorship(), p.b().authorship(), p.scientificName(), p.nidx(), p.a().datasetKey(), p.b().datasetKey()));
      }
    }
  }

  private static String code(AuthorPair p) {
    return p.code() == null ? "-" : p.code().name();
  }

  public static void report(File pairs, File outDir, AuthorComparator comparator) throws IOException {
    final AuthorshipNormalizer normalizer = AuthorshipNormalizer.INSTANCE;
    final CorpusEvaluator evaluator = new CorpusEvaluator(comparator);
    final CorpusEvaluator.Result result = new CorpusEvaluator.Result(false);

    List<Top> tops = List.of(
      new Top("Same act judged DIFFERENT by its authors", "The verdict is DIFFERENT even with the years taken out.",
        v -> v.pair().label() == Label.SAME && v.verdictNoYear() == Equality.DIFFERENT),
      new Top("Same act judged DIFFERENT by its years only", "The authors alone are not judged DIFFERENT, the years make it so.",
        v -> v.pair().label() == Label.SAME && v.verdict() == Equality.DIFFERENT && v.verdictNoYear() != Equality.DIFFERENT),
      new Top("Different names judged EQUAL", "A dataset keeps these two apart and gives them different years.",
        v -> v.pair().label() == Label.DIFF && v.verdict() == Equality.EQUAL),
      new Top("Different names judged EQUAL by their authors",
        "The same pairs with the years taken out: homonyms the authors alone do not tell apart.",
        v -> v.pair().label() == Label.DIFF && v.verdict() != Equality.EQUAL && v.verdictNoYear() == Equality.EQUAL),
      new Top("Same act judged UNKNOWN", "Mostly a basionym author on one side against a combination author on the other.",
        v -> v.pair().label() == Label.SAME && v.verdict() == Equality.UNKNOWN),
      new Top("Alias candidates the author map lacks", "Same act, one author differs, and the map does not bring the two to one canonical.",
        v -> v.pair().label() == Label.SAME && v.pair().weight() >= MIN_ALIAS_WEIGHT && lacksAlias(normalizer, v.pair())),
      new Top("Dubious pairs judged EQUAL",
        "Kept apart by one dataset with nothing to tell a homonym from a duplicate. Judged EQUAL it is most "
        + "likely a second record of the name in that dataset. Not part of any matrix.",
        v -> v.pair().label() == Label.DUBIOUS && v.verdict() == Equality.EQUAL),
      new Top("Dubious pairs judged DIFFERENT",
        "The same, judged DIFFERENT: homonyms, or duplicates the comparison does not see. INTRA_CROSS is the one "
        + "to read, those are also cited alike across datasets. Not part of any matrix.",
        v -> v.pair().label() == Label.DUBIOUS && v.verdict() == Equality.DIFFERENT)
    );
    // pairs a dataset keeps apart, and how many of them the comparator takes for one: a high share points at duplicate records
    Map<Integer, long[]> diffByDataset = new TreeMap<>();
    Map<Source, long[]> dubiousBySource = new EnumMap<>(Source.class);
    Map<Label, Integer> labels = new EnumMap<>(Label.class);
    int[] total = new int[1];

    outDir.mkdirs();
    try (TabWriter verdicts = CorpusIO.writer(new File(outDir, VERDICTS), AuthorVerdictDiff.COLUMNS)) {
      CorpusIO.readPairs(pairs, p -> {
        Verdict v = evaluator.evaluate(p);
        result.add(v);
        tops.forEach(t -> t.offer(v));
        total[0]++;
        labels.merge(p.label(), 1, Integer::sum);
        long[] n = p.label() == Label.DIFF ? diffByDataset.computeIfAbsent(p.a().datasetKey(), k -> new long[2])
                 : p.label() == Label.DUBIOUS ? dubiousBySource.computeIfAbsent(p.source(), k -> new long[2]) : null;
        if (n != null) {
          n[0]++;
          if (v.verdict() == Equality.EQUAL) n[1]++;
        }
        try {
          verdicts.write(new AuthorVerdictDiff.VerdictRow(p.code() == null ? "" : p.code().name(), p.keyA(), p.keyB(),
            p.label().name(), p.source().name(),
            p.weight(), v.verdict().name(), v.verdictNoYear().name(), p.a().authorship(), p.b().authorship()).toRow());
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
    }

    StringBuilder sb = new StringBuilder();
    sb.append("# Author comparison against the corpus\n\n");
    sb.append("input: ").append(pairs).append('\n');
    sb.append(String.format("pairs: %,d %s%n", total[0], labels));
    // says which author map the verdicts come from: -pl dao takes it from the api jar in ~/.m2, not from the checkout
    sb.append(String.format("author map: %,d rows%n", Resources.lines(AUTHOR_MAP).count()));
    sb.append(String.format("max heap: %,d MB%n", Runtime.getRuntime().maxMemory() >> 20));

    sb.append("\n## Confusion matrix\nWith the years and the code of the names, which is what production gets to see. "
      + "Right is SAME = EQUAL and DIFF = DIFFERENT.\n\n");
    CorpusEvaluator.SCOPES.forEach(scope -> sb.append(result.matrix(scope, true).render(scope)).append('\n'));
    sb.append("## Confusion matrix without years\nThe labels speak about authors, and this is the author logic alone.\n\n");
    CorpusEvaluator.SCOPES.forEach(scope -> sb.append(result.matrix(scope, false).render(scope)).append('\n'));

    sb.append("## Different names by dataset\nA dataset far above the others holds duplicate records rather than homonyms.\n");
    diffByDataset.forEach((key, n) -> sb.append(String.format("  %-8d %,9d pairs, %5.1f%% judged EQUAL%n", key, n[0], 100d * n[1] / n[0])));
    sb.append("\n## Dubious pairs by what makes them dubious\n"
      + "Not part of any matrix. The share judged EQUAL is roughly the share of duplicate records.\n");
    dubiousBySource.forEach((source, n) ->
      sb.append(String.format("  %-16s %,9d pairs, %5.1f%% judged EQUAL%n", source, n[0], 100d * n[1] / n[0])));

    tops.forEach(t -> t.render(sb));
    Files.writeString(new File(outDir, REPORT).toPath(), sb.toString(), StandardCharsets.UTF_8);
  }

  /**
   * @return true if exactly one team differs between the two names, holds a single author on both sides, and the
   * author map does not resolve the two to the same canonical
   */
  private static boolean lacksAlias(AuthorshipNormalizer normalizer, AuthorPair p) {
    List<List<String>> a = List.of(p.a().basExAuthors(), p.a().basAuthors(), p.a().combExAuthors(), p.a().combAuthors());
    List<List<String>> b = List.of(p.b().basExAuthors(), p.b().basAuthors(), p.b().combExAuthors(), p.b().combAuthors());
    String x = null;
    String y = null;
    for (int i = 0; i < a.size(); i++) {
      if (!a.get(i).equals(b.get(i))) {
        if (x != null || a.get(i).size() != 1 || b.get(i).size() != 1) {
          return false;
        }
        x = a.get(i).get(0);
        y = b.get(i).get(0);
      }
    }
    if (x == null) {
      return false;
    }
    String cx = normalizer.lookup(AuthorshipNormalizer.normalize(x), p.code());
    String cy = normalizer.lookup(AuthorshipNormalizer.normalize(y), p.code());
    return !Objects.equals(cx, cy);
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 2 || !new File(args[0]).isFile()) {
      System.err.println("Usage: AuthorCorpusReport <pairs.tsv[.gz]> <output directory>");
      System.exit(1);
    }
    File outDir = new File(args[1]);
    report(new File(args[0]), outDir, new AuthorComparator(AuthorshipNormalizer.INSTANCE));
    System.out.println(Files.readString(new File(outDir, REPORT).toPath()).lines().limit(60).reduce("", (x, y) -> x + y + "\n"));
    System.out.println("Full report in " + new File(outDir, REPORT));
  }
}
