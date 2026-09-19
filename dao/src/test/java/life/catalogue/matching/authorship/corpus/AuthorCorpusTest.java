package life.catalogue.matching.authorship.corpus;

import life.catalogue.common.io.Resources;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.matching.Equality;
import life.catalogue.matching.authorship.AuthorComparator;
import life.catalogue.matching.authorship.corpus.LabelRules.Label;

import org.gbif.nameparser.api.NomCode;

import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Guards the author comparison against getting worse on a frozen sample of the corpus mined from ChecklistBank.
 * <p>
 * The numbers are what master did on 2026-09-19, not what is right: 5% of the same acts are still judged DIFFERENT.
 * A change that moves them is meant to - look at what flipped with AuthorVerdictDiff on the full corpus, then set
 * the new numbers here. See docs/AUTHOR-CORPUS.md
 */
public class AuthorCorpusTest {
  static final String SAMPLE = "author-corpus/author-pairs-sample.tsv.gz";
  /** the share a number may get worse by before the test fails */
  private static final double HEADROOM = 0.05;

  private static List<AuthorPair> pairs;
  private static CorpusEvaluator.Result result;

  @BeforeClass
  public static void evaluate() throws Exception {
    pairs = CorpusIO.readPairs(Resources.toFile(SAMPLE));
    result = new CorpusEvaluator(new AuthorComparator(AuthorshipNormalizer.INSTANCE)).evaluate(pairs);
    for (String scope : CorpusEvaluator.SCOPES) {
      System.out.println(result.matrix(scope, true).render(scope));
    }
  }

  /** the pins below are only worth something for the very pairs they were taken from */
  @Test
  public void sample() {
    assertEquals(16679, pairs.size());
  }

  @Test
  public void all() {
    assertPinned(CorpusEvaluator.ALL, 7283, 350, 8604, 114);
  }

  @Test
  public void botanical() {
    assertPinned(NomCode.BOTANICAL.name(), 2860, 137, 2959, 53);
  }

  @Test
  public void zoological() {
    assertPinned(NomCode.ZOOLOGICAL.name(), 2859, 175, 2715, 37);
  }

  /**
   * @param sameEqual     same acts judged EQUAL, which may not drop
   * @param sameDifferent same acts judged DIFFERENT, which may not grow
   * @param diffDifferent different names judged DIFFERENT, which may not drop
   * @param diffEqual     different names judged EQUAL, which may not grow
   */
  private static void assertPinned(String scope, int sameEqual, int sameDifferent, int diffDifferent, int diffEqual) {
    ConfusionMatrix m = result.matrix(scope, true);
    String actual = String.format("%s is now: %d, %d, %d, %d", scope,
      m.count(Label.SAME, Equality.EQUAL), m.count(Label.SAME, Equality.DIFFERENT),
      m.count(Label.DIFF, Equality.DIFFERENT), m.count(Label.DIFF, Equality.EQUAL));
    assertTrue("fewer same acts judged EQUAL. " + actual, m.count(Label.SAME, Equality.EQUAL) >= sameEqual * (1 - HEADROOM));
    assertTrue("more same acts judged DIFFERENT. " + actual, m.count(Label.SAME, Equality.DIFFERENT) <= sameDifferent * (1 + HEADROOM));
    assertTrue("fewer different names judged DIFFERENT. " + actual,
      m.count(Label.DIFF, Equality.DIFFERENT) >= diffDifferent * (1 - HEADROOM));
    assertTrue("more different names judged EQUAL. " + actual, m.count(Label.DIFF, Equality.EQUAL) <= diffEqual * (1 + HEADROOM));
  }
}
