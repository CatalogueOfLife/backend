package life.catalogue.matching.person;

import life.catalogue.api.model.Person;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.matching.authorship.AuthorComparator;
import life.catalogue.matching.authorship.StringAuthorMatcher;
import life.catalogue.matching.authorship.corpus.AuthorCorpusReport;
import life.catalogue.matching.authorship.corpus.AuthorPair;
import life.catalogue.matching.authorship.corpus.AuthorVerdictDiff;
import life.catalogue.matching.authorship.corpus.CorpusEvaluator.Verdict;
import life.catalogue.matching.person.PersonAuthorMatcher.Decision;
import life.catalogue.matching.person.PersonAuthorMatcher.Explanation;
import life.catalogue.matching.person.PersonAuthorMatcher.RelativesPolicy;
import life.catalogue.matching.person.PersonAuthorMatcher.Rule;
import life.catalogue.matching.person.PersonResolver.Margins;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

/**
 * Measures the person matcher on the corpus: the report of {@link AuthorCorpusReport} under both relatives policies, plus
 * which rule decided the verdicts, how many citations resolved, the citations no person resolves ranked by names, every
 * pair the relatives policy decided, and the flips against the string matcher's verdicts. See docs/AUTHOR-CORPUS.md.
 * <pre>
 * mvn -q -pl dao -am install -DskipTests
 * cd core
 * mvn -q test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
 *   -Dexec.args="-Xmx6g -cp %classpath life.catalogue.matching.person.PersonCorpusReport \
 *     ../dao/target/author-corpus/classified/pairs.tsv.gz ../dao/target/author-corpus/classified/verdicts.tsv.gz \
 *     target/person-corpus [--min-age N] [--posthumous N] [--active-slack N]"
 * </pre>
 */
public class PersonCorpusReport {
  static final String DIFF = "diff.txt";
  private static final int TOP = 200;

  /** attributes the explanations of the matcher to the pair being judged */
  static class Explaining implements AuthorCorpusReport.Extension, Consumer<Explanation> {
    private final long start = System.nanoTime();
    private final List<Explanation> current = new ArrayList<>();
    private final Map<String, long[]> decidedBy = new TreeMap<>();
    private final Map<String, Long> unresolved = new HashMap<>();
    private final List<Map.Entry<Integer, String>> relatives = new ArrayList<>();
    private long citations;
    private long resolved;
    private long citationNames;
    private long resolvedNames;

    @Override
    public void accept(Explanation e) {
      current.add(e);
    }

    @Override
    public void before(AuthorPair pair) {
      current.clear();
    }

    @Override
    public void after(Verdict v) {
      AuthorPair p = v.pair();
      Set<Rule> rules = EnumSet.noneOf(Rule.class);
      Set<String> known = new TreeSet<>();
      Set<String> unknown = new TreeSet<>();
      List<String> related = new ArrayList<>();
      for (Explanation e : current) {
        for (Decision d : e.decisions()) {
          rules.add(d.rule());
          if (d.rule() == Rule.IDENTICAL) continue;
          (d.persons1().isEmpty() ? unknown : known).add(d.author1());
          (d.persons2().isEmpty() ? unknown : known).add(d.author2());
          if (d.rule() == Rule.RELATIVES) {
            related.add(ids(d.persons1()) + " / " + ids(d.persons2()));
          }
        }
      }
      unknown.removeAll(known);
      String key = (rules.isEmpty() ? "years alone" : rules.stream().map(Enum::name).collect(Collectors.joining("+")))
        + " -> " + v.verdict();
      long[] n = decidedBy.computeIfAbsent(key, k -> new long[2]);
      n[0]++;
      n[1] += p.weight();
      citations += known.size() + unknown.size();
      resolved += known.size();
      citationNames += (long) (known.size() + unknown.size()) * p.weight();
      resolvedNames += (long) known.size() * p.weight();
      unknown.forEach(c -> unresolved.merge(c, (long) p.weight(), Long::sum));
      if (!related.isEmpty()) {
        relatives.add(Map.entry(p.weight(), String.format("%6d  %-5s %-9s %s | %s   [%s]", p.weight(), p.label(), v.verdict(),
          p.a().authorship(), p.b().authorship(), String.join(", ", related))));
      }
    }

    private static String ids(Set<Person> persons) {
      return persons.stream().map(Person::id).collect(Collectors.joining(","));
    }

    @Override
    public void render(StringBuilder sb) {
      sb.append("\n## What decided the verdicts\nThe rules of every author pair a name pair needed, and the verdict.\n");
      decidedBy.forEach((k, n) -> sb.append(String.format("  %-40s %,9d pairs %,11d names%n", k, n[0], n[1])));
      sb.append(String.format("%n## Citations resolved%n  %.1f%% of %,d citations, %.1f%% weighted by names%n",
        pct(resolved, citations), citations, pct(resolvedNames, citationNames)));
      sb.append(String.format("%n## Citations no person resolves%n%,d citations, the first %d by names:%n", unresolved.size(), TOP));
      unresolved.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(TOP)
        .forEach(e -> sb.append(String.format("  %9d  %s%n", e.getValue(), e.getKey())));
      sb.append(String.format("%n## Pairs the relatives policy decided%n%,d pairs, the first %d by names:%n", relatives.size(), TOP));
      relatives.stream().sorted(Map.Entry.<Integer, String>comparingByKey().reversed()).limit(TOP)
        .forEach(e -> sb.append("  ").append(e.getValue()).append('\n'));
      sb.append(String.format("%n## Run%n  %,d s%n", (System.nanoTime() - start) / 1_000_000_000L));
    }

    private static double pct(long part, long total) {
      return total == 0 ? 0 : 100d * part / total;
    }
  }

  public static void report(File pairs, @Nullable File stringVerdicts, File outDir, Margins margins) throws IOException {
    long t0 = System.nanoTime();
    MemoryPersonStore registry = MemoryPersonStore.resources();
    long loadMs = (System.nanoTime() - t0) / 1_000_000;
    System.gc();
    long heapMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) >> 20;
    var strings = new StringAuthorMatcher(AuthorshipNormalizer.INSTANCE);
    for (RelativesPolicy policy : RelativesPolicy.values()) {
      File dir = new File(outDir, policy.name().toLowerCase(Locale.ROOT));
      var explaining = new Explaining();
      var matcher = new PersonAuthorMatcher(registry, new PersonResolver(registry, margins), strings, policy, explaining);
      AuthorCorpusReport.report(pairs, dir, new AuthorComparator(matcher), List.of(
        "comparator: persons, relatives " + policy,
        String.format("registry: %,d persons, loaded in %,d ms, heap in use after loading %,d MB", registry.size(), loadMs, heapMb),
        "margins: " + margins), null, explaining);
      if (stringVerdicts != null) {
        var diff = AuthorVerdictDiff.diff(AuthorVerdictDiff.read(stringVerdicts),
          AuthorVerdictDiff.read(new File(dir, AuthorCorpusReport.VERDICTS)));
        Files.writeString(new File(dir, DIFF).toPath(), AuthorVerdictDiff.render(diff), StandardCharsets.UTF_8);
      }
    }
  }

  static Margins margins(String[] args, int from) {
    int minAge = Margins.DEFAULT.minAge();
    int posthumous = Margins.DEFAULT.posthumous();
    int activeSlack = Margins.DEFAULT.activeSlack();
    for (int i = from; i + 1 < args.length; i += 2) {
      int v = Integer.parseInt(args[i + 1]);
      switch (args[i]) {
        case "--min-age" -> minAge = v;
        case "--posthumous" -> posthumous = v;
        case "--active-slack" -> activeSlack = v;
        default -> throw new IllegalArgumentException("Unknown option " + args[i]);
      }
    }
    return new Margins(minAge, posthumous, activeSlack);
  }

  public static void main(String[] args) throws IOException {
    if (args.length < 3 || !new File(args[0]).isFile()) {
      System.err.println("Usage: PersonCorpusReport <pairs> <string verdicts> <output directory> "
        + "[--min-age N] [--posthumous N] [--active-slack N]");
      System.exit(1);
    }
    File outDir = new File(args[2]);
    report(new File(args[0]), new File(args[1]), outDir, margins(args, 3));
    for (RelativesPolicy policy : RelativesPolicy.values()) {
      File dir = new File(outDir, policy.name().toLowerCase(Locale.ROOT));
      System.out.println(Files.readString(new File(dir, AuthorCorpusReport.REPORT).toPath()).lines().limit(25)
        .collect(Collectors.joining("\n")));
      System.out.println("Full report in " + dir);
    }
  }
}
