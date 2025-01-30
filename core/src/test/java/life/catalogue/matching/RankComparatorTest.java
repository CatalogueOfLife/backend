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
}