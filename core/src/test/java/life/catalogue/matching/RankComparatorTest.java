package life.catalogue.matching;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RankComparatorTest {

  @Test
  public void compare() {
    for (Rank r : Rank.values()) {
      assertEquals(Equality.EQUAL, RankComparator.compare(r, r));
      if (r == Rank.OTHER) continue;
      Equality expected = Equality.UNKNOWN;
      if  (r == Rank.UNRANKED) {
        expected = Equality.EQUAL;
      }
      assertEquals(expected, RankComparator.compare(r, null));
      assertEquals(expected, RankComparator.compare(Rank.UNRANKED, r));
    }
    assertEquals(Equality.UNKNOWN, RankComparator.compare(Rank.SUPRAGENERIC_NAME, Rank.UNRANKED));
    assertEquals(Equality.UNKNOWN, RankComparator.compare(Rank.SUPRAGENERIC_NAME, Rank.FAMILY));
    assertEquals(Equality.UNKNOWN, RankComparator.compare(Rank.SUPRAGENERIC_NAME, Rank.KINGDOM));
    assertEquals(Equality.DIFFERENT, RankComparator.compare(Rank.SUPRAGENERIC_NAME, Rank.SPECIES));
  }

  @Test
  public void scorePenalty() {
    assertEquals(0, RankComparator.scorePenalty(Rank.CLASS, Rank.CLASS));
    assertEquals(0, RankComparator.scorePenalty(null, Rank.CLASS));
    assertEquals(0, RankComparator.scorePenalty(Rank.UNRANKED, Rank.CLASS));
    assertEquals(0, RankComparator.scorePenalty(Rank.SUPRAGENERIC_NAME, Rank.FAMILY));
    assertEquals(2, RankComparator.scorePenalty(Rank.SUBCLASS, Rank.CLASS));
    assertEquals(2, RankComparator.scorePenalty(Rank.FAMILY, Rank.SUBFAMILY));
    assertEquals(5, RankComparator.scorePenalty(Rank.CLASS, Rank.PARVPHYLUM));
    assertEquals(5, RankComparator.scorePenalty(Rank.KINGDOM, Rank.DOMAIN));
    assertEquals(5, RankComparator.scorePenalty(Rank.SUBFAMILY, Rank.TRIBE));
  }
}
