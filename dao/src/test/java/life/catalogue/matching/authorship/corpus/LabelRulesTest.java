package life.catalogue.matching.authorship.corpus;

import org.junit.Test;

import static life.catalogue.matching.authorship.corpus.LabelRules.Label.*;
import static life.catalogue.matching.authorship.corpus.LabelRules.Source.*;
import static org.junit.Assert.assertEquals;

public class LabelRulesTest {
  private final LabelRules rules = LabelRules.DEFAULT;

  /** a pair only ever seen across datasets */
  private static PairStat cross(int support, int yearAgree, int yearConflict, int freqA, int freqB) {
    return new PairStat(support, yearAgree, yearConflict, freqA, freqB, 0, 0, 0);
  }

  /** a pair seen inside a dataset, with the given support across datasets */
  private static PairStat intra(int yearDiff, int noYear, int yearAgree, int crossSupport) {
    return new PairStat(crossSupport, 0, 0, 100, 100, yearDiff, noYear, yearAgree);
  }

  private void assertLabel(LabelRules.Label label, LabelRules.Source source, PairStat stat) {
    var l = rules.label(stat);
    assertEquals(label, l.label());
    assertEquals(source, l.source());
  }

  @Test
  public void recurringAliasIsTheSame() {
    assertLabel(SAME, CROSS, cross(3, 0, 0, 10, 400));
  }

  @Test
  public void tooFewNamesStayUnlabelled() {
    assertLabel(UNLABELLED, CROSS, cross(2, 0, 0, 10, 400));
  }

  /**
   * Two prolific authors of the same genera meet on a few names by chance,
   * an alias meets on a good share of the names its rarer spelling has.
   */
  @Test
  public void rareAmongTheNamesOfBothStaysUnlabelled() {
    assertLabel(UNLABELLED, CROSS, cross(3, 0, 0, 100, 5000));
    assertLabel(SAME, CROSS, cross(5, 0, 0, 100, 5000));
  }

  /** zoological authors of a few names never reach the support, but their years agree */
  @Test
  public void agreeingYearsOnTwoNamesAreEnough() {
    assertLabel(SAME, CROSS_YEAR, cross(2, 2, 0, 400, 5000));
    assertLabel(UNLABELLED, CROSS, cross(2, 1, 0, 400, 5000));
  }

  @Test
  public void conflictingYearsVeto() {
    assertLabel(UNLABELLED, CROSS, cross(4, 0, 2, 10, 10));
    assertLabel(SAME, CROSS, cross(9, 0, 1, 10, 10));
  }

  @Test
  public void keptApartByOneDatasetWithOtherYearsIsDifferent() {
    assertLabel(DIFF, INTRA_YEARDIFF, intra(1, 0, 0, 0));
    assertLabel(DIFF, INTRA_YEARDIFF, intra(1, 3, 0, 0));
  }

  /**
   * Without a year nothing tells a homonym from a second record of the same name, and the nomenclators are full of
   * those: "Pohl" next to "Pohl ex Benth.", "Kuntze" next to "(Nees ex DC.) Kuntze". In the first corpus 19% of
   * these pairs compared EQUAL against 3% of the ones with conflicting years, nearly all of them such records.
   */
  @Test
  public void keptApartWithoutAnyYearIsDubious() {
    assertLabel(DUBIOUS, INTRA_NOYEAR, intra(0, 2, 0, 0));
  }

  /** the same year inside one dataset is an isonym or a duplicate record rather than a homonym */
  @Test
  public void sameYearInsideOneDatasetIsDubious() {
    assertLabel(DUBIOUS, INTRA_YEARAGREE, intra(0, 0, 1, 0));
    assertLabel(DUBIOUS, INTRA_YEARAGREE, intra(4, 0, 1, 0));
  }

  @Test
  public void anySupportAsAliasMakesADifferenceDubious() {
    assertLabel(DUBIOUS, INTRA_CROSS, intra(1, 0, 0, 1));
  }

  /** a duplicate record in one dataset must not take a well supported alias out of the positives */
  @Test
  public void wellSupportedAliasSurvivesADuplicateRecord() {
    assertLabel(SAME, CROSS, new PairStat(50, 0, 0, 100, 100, 0, 1, 0));
  }

  @Test
  public void weightIsWhatBacksTheLabel() {
    assertEquals(50, rules.label(new PairStat(50, 0, 0, 100, 100, 0, 1, 0)).weight());
    assertEquals(4, rules.label(intra(1, 3, 0, 0)).weight());
  }
}
