package life.catalogue.matching.person;

import life.catalogue.common.io.Resources;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.matching.Equality;
import life.catalogue.matching.authorship.AuthorComparator;
import life.catalogue.matching.authorship.StringAuthorMatcher;
import life.catalogue.matching.authorship.corpus.AuthorPair;
import life.catalogue.matching.authorship.corpus.ConfusionMatrix;
import life.catalogue.matching.authorship.corpus.CorpusEvaluator;
import life.catalogue.matching.authorship.corpus.CorpusIO;
import life.catalogue.matching.authorship.corpus.LabelRules.Label;
import life.catalogue.matching.person.PersonAuthorMatcher.RelativesPolicy;

import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Relatives the corpus hardly holds as DIFF, since a dataset rarely lists both: the fixture pins what both matchers do.
 * Like CorpusEvaluatorTest.knownMisjudgements it is a characterisation, not a statement of what is right: whoever
 * moves the numbers moves them here, which is the point.
 */
public class RelativesFixtureTest {
  static final String FIXTURE = "author-corpus/relatives-pairs.tsv";
  private static List<AuthorPair> pairs;

  @BeforeClass
  public static void load() throws Exception {
    pairs = CorpusIO.readPairs(Resources.toFile(FIXTURE));
  }

  static AuthorComparator persons(RelativesPolicy policy) {
    PersonRegistry reg = PersonRegistry.get();
    return new AuthorComparator(new PersonAuthorMatcher(reg, new PersonResolver(reg, PersonResolver.Margins.DEFAULT),
      new StringAuthorMatcher(AuthorshipNormalizer.INSTANCE), policy, null));
  }

  static ConfusionMatrix matrix(AuthorComparator comparator, boolean withYears) {
    return new CorpusEvaluator(comparator).evaluate(pairs).matrix(CorpusEvaluator.ALL, withYears);
  }

  @Test
  public void fixture() {
    assertEquals(51, pairs.size());
    assertEquals(30, pairs.stream().filter(p -> p.label() == Label.DIFF).count());
  }

  @Test
  public void strings() {
    for (boolean years : new boolean[]{true, false}) {
      ConfusionMatrix m = matrix(new AuthorComparator(AuthorshipNormalizer.INSTANCE), years);
      assertEquals(15, m.count(Label.DIFF, Equality.EQUAL));
      assertEquals(15, m.count(Label.DIFF, Equality.DIFFERENT));
      assertEquals(17, m.count(Label.SAME, Equality.EQUAL));
      assertEquals(4, m.count(Label.SAME, Equality.DIFFERENT));
    }
  }

  /**
   * Relatives are UNKNOWN, which the comparator combines with the years: only G. B. Sowerby II and III, both cited 1874,
   * come out EQUAL; years a few apart make DIFFERENT and the botanical pairs without years stay UNKNOWN, all but
   * C.DC. / DC., grandson and grandfather, whom no relation joins. Every alias pair is one person.
   */
  @Test
  public void personsRelativesUnknown() {
    ConfusionMatrix m = matrix(persons(RelativesPolicy.UNKNOWN), true);
    assertEquals(1, m.count(Label.DIFF, Equality.EQUAL));
    assertEquals(15, m.count(Label.DIFF, Equality.DIFFERENT));
    assertEquals(14, m.count(Label.DIFF, Equality.UNKNOWN));
    assertEquals(21, m.count(Label.SAME, Equality.EQUAL));
    assertEquals(0, m.count(Label.SAME, Equality.DIFFERENT));
    assertEquals(0, m.count(Label.SAME, Equality.UNKNOWN));
  }

  /** relatives are different persons: every one of them is kept apart, and every alias pair is one person */
  @Test
  public void personsRelativesDifferent() {
    ConfusionMatrix m = matrix(persons(RelativesPolicy.DIFFERENT), true);
    assertEquals(0, m.count(Label.DIFF, Equality.EQUAL));
    assertEquals(30, m.count(Label.DIFF, Equality.DIFFERENT));
    assertEquals(0, m.count(Label.DIFF, Equality.UNKNOWN));
    assertEquals(21, m.count(Label.SAME, Equality.EQUAL));
    assertEquals(0, m.count(Label.SAME, Equality.DIFFERENT));
    assertEquals(0, m.count(Label.SAME, Equality.UNKNOWN));
  }

  /** prints every verdict of all three, for the Outcome and for whoever moves the numbers */
  public static void main(String[] args) throws Exception {
    load();
    var strings = new CorpusEvaluator(new AuthorComparator(AuthorshipNormalizer.INSTANCE)).evaluate(pairs).verdicts();
    var unknown = new CorpusEvaluator(persons(RelativesPolicy.UNKNOWN)).evaluate(pairs).verdicts();
    var different = new CorpusEvaluator(persons(RelativesPolicy.DIFFERENT)).evaluate(pairs).verdicts();
    System.out.println("label  strings    unknown    different  | authorships");
    for (int i = 0; i < pairs.size(); i++) {
      AuthorPair p = pairs.get(i);
      System.out.printf("%-6s %-10s %-10s %-10s | %s | %s%n", p.label(), strings.get(i).verdict(), unknown.get(i).verdict(),
        different.get(i).verdict(), p.a().authorship(), p.b().authorship());
    }
  }
}
