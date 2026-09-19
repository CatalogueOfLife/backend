package life.catalogue.matching.authorship.corpus;

import life.catalogue.matching.authorship.corpus.LabelRules.Label;
import life.catalogue.matching.authorship.corpus.LabelRules.Source;

import org.gbif.nameparser.api.NomCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AuthorPairSamplerTest {

  private static AuthorPair pair(Label label, NomCode code, int n, int weight) {
    var a = new AuthorPair.Side(1, "a" + n, "A" + n, List.of("A" + n), List.of(), null, List.of(), List.of(), null, null);
    var b = new AuthorPair.Side(2, "b" + n, "B" + n, List.of("B" + n), List.of(), null, List.of(), List.of(), null, null);
    return new AuthorPair(label, label == Label.SAME ? Source.CROSS : Source.INTRA_YEARDIFF, weight,
      new PairStat(weight, 0, 0, 9, 9, 0, 0, 0),
      code, "SPECIES", n, "Aus bus", ";;;a" + n, ";;;b" + n, a, b);
  }

  private static List<AuthorPair> corpus() {
    List<AuthorPair> pairs = new ArrayList<>();
    for (int n = 0; n < 5000; n++) {
      pairs.add(pair(Label.SAME, NomCode.BOTANICAL, n, n));
    }
    for (int n = 5000; n < 5040; n++) {
      pairs.add(pair(Label.DIFF, NomCode.ZOOLOGICAL, n, 1));
    }
    pairs.add(pair(Label.DUBIOUS, NomCode.BOTANICAL, 6000, 50));
    pairs.add(pair(Label.UNLABELLED, null, 6001, 50));
    return pairs;
  }

  @Test
  public void keepsTheBestSupportedOfEveryStratum() {
    var sample = new AuthorPairSampler(100, 200).sample(corpus());
    var same = sample.stream().filter(p -> p.label() == Label.SAME).toList();
    for (int n = 4900; n < 5000; n++) {
      int id = n;
      assertTrue("missing " + n, same.stream().anyMatch(p -> p.nidx() == id));
    }
  }

  @Test
  public void addsATailOfAboutTheWantedSize() {
    var sample = new AuthorPairSampler(100, 200).sample(corpus());
    long tail = sample.stream().filter(p -> p.label() == Label.SAME && p.nidx() < 4900).count();
    assertTrue("tail of " + tail, tail > 120 && tail < 280);
  }

  /** a stratum smaller than what is asked for is taken whole */
  @Test
  public void takesSmallStrataWhole() {
    var sample = new AuthorPairSampler(100, 200).sample(corpus());
    assertEquals(40, sample.stream().filter(p -> p.label() == Label.DIFF).count());
  }

  @Test
  public void onlyWhatIsMeasured() {
    var sample = new AuthorPairSampler(100, 200).sample(corpus());
    assertTrue(sample.stream().allMatch(p -> p.label() == Label.SAME || p.label() == Label.DIFF));
  }

  /** the sample must not depend on the order of the corpus, or a new export rewrites the committed file */
  @Test
  public void isDeterministicAndSorted() {
    var shuffled = corpus();
    Collections.shuffle(shuffled, new Random(7));
    var s1 = new AuthorPairSampler(100, 200).sample(corpus());
    var s2 = new AuthorPairSampler(100, 200).sample(shuffled);
    assertEquals(s1, s2);

    var ids = s1.stream().map(p -> p.label() + " " + p.id()).toList();
    assertEquals(ids.stream().sorted().toList(), ids);
  }
}
