package life.catalogue.matching.authorship.corpus;

import life.catalogue.common.io.TabWriter;
import life.catalogue.matching.authorship.corpus.LabelRules.Label;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.common.hash.Hashing;

/**
 * Draws the sample of the corpus that is committed for the guard test.
 * <p>
 * Per label and nomenclatural code it keeps the pairs backed by most names and adds a tail selected by a hash of
 * the pair, so the sample neither depends on the order of the corpus nor changes much when the corpus is mined
 * again. The comparator is deliberately not asked: a sample of what it gets wrong today would stop measuring
 * anything the day that is fixed.
 *
 * Run it in a forked JVM, paths are relative to the dao module. See docs/AUTHOR-CORPUS.md
 * <pre>
 * mvn -q -pl dao test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
 *   -Dexec.args="-Xmx4g -cp %classpath life.catalogue.matching.authorship.corpus.AuthorPairSampler \
 *     target/author-corpus/pairs.tsv.gz src/test/resources/author-corpus/author-pairs-sample.tsv.gz"
 * </pre>
 */
public class AuthorPairSampler {
  static final int TOP = 1000;
  static final int TAIL = 2000;
  private static final int BUCKETS = 10_000;
  private static final Comparator<AuthorPair> BY_WEIGHT = Comparator.comparingInt(AuthorPair::weight).reversed()
                                                                    .thenComparing(AuthorPair::id);
  private static final Comparator<AuthorPair> BY_ID = Comparator.comparing((AuthorPair p) -> p.label().name())
                                                                .thenComparing(AuthorPair::id);

  private final int top;
  private final int tail;

  /**
   * @param top  pairs of a stratum to keep for their weight
   * @param tail pairs of a stratum to add from the rest, approximately
   */
  public AuthorPairSampler(int top, int tail) {
    this.top = top;
    this.tail = tail;
  }

  public List<AuthorPair> sample(List<AuthorPair> pairs) {
    Map<String, List<AuthorPair>> strata = new TreeMap<>();
    for (AuthorPair p : pairs) {
      if (p.label() == Label.SAME || p.label() == Label.DIFF) {
        strata.computeIfAbsent(p.label() + " " + CorpusEvaluator.scope(p.code()), k -> new ArrayList<>()).add(p);
      }
    }
    List<AuthorPair> sample = new ArrayList<>();
    for (List<AuthorPair> stratum : strata.values()) {
      stratum.sort(BY_WEIGHT);
      List<AuthorPair> rest = stratum.subList(Math.min(top, stratum.size()), stratum.size());
      sample.addAll(stratum.subList(0, stratum.size() - rest.size()));
      // a threshold on a hash of the pair, not a random draw: a corpus mined again moves the threshold a little and with it few pairs
      long threshold = rest.isEmpty() ? 0 : Math.min(BUCKETS, (long) Math.ceil((double) BUCKETS * tail / rest.size()));
      for (AuthorPair p : rest) {
        if (Math.floorMod(Hashing.murmur3_32_fixed().hashString(p.id(), StandardCharsets.UTF_8).asInt(), BUCKETS) < threshold) {
          sample.add(p);
        }
      }
    }
    sample.sort(BY_ID);
    return sample;
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 2 || !new File(args[0]).isFile()) {
      System.err.println("Usage: AuthorPairSampler <pairs.tsv[.gz]> <sample.tsv[.gz]>");
      System.exit(1);
    }
    System.out.printf("Max heap %,d MB%n", Runtime.getRuntime().maxMemory() >> 20);
    List<AuthorPair> sample = new AuthorPairSampler(TOP, TAIL).sample(CorpusIO.readPairs(new File(args[0])));
    try (TabWriter writer = CorpusIO.pairWriter(new File(args[1]))) {
      for (AuthorPair p : sample) {
        writer.write(p.toRow());
      }
    }
    System.out.printf("Wrote %,d pairs to %s%n", sample.size(), args[1]);
  }
}
