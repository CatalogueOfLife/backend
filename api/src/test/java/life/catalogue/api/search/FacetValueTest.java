package life.catalogue.api.search;

import life.catalogue.api.vocab.Issue;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class FacetValueTest {

  @Test
  public void testForInteger() {
    // Both should work. First one initially broke facet query request b/c it didn't take into account that integers might be stored using
    // the keyword datatype for performance reasons.
    var f = FacetValue.forInteger("1010", 3, null);
    assertNull(f.getLabel());
    assertEquals(1010, (int) f.getValue());
    assertEquals(3, f.getCount());

    f = FacetValue.forInteger(1010, 3, k -> "title #"+k);
    assertEquals("title #1010", f.getLabel());
    assertEquals(1010, (int) f.getValue());
    assertEquals(3, f.getCount());
  }

  @Test
  public void testForEnumValid() {
    var f = FacetValue.forEnum(Issue.class, Issue.NAME_NOT_UNIQUE.name(), 5);
    assertNotNull(f);
    assertEquals(Issue.NAME_NOT_UNIQUE, f.getValue());
    assertEquals(5, f.getCount());
  }

  @Test
  public void testForEnumValidRank() {
    var f = FacetValue.forEnum(Rank.class, String.valueOf(Rank.SPECIES.ordinal()), 7);
    assertNotNull(f);
    assertEquals(Rank.SPECIES, f.getValue());
    assertEquals(7, f.getCount());
  }

  @Test
  public void testForEnumUnknownNameReturnsNull() {
    // EnumUtils.getEnum returns null for unknown names rather than throwing — must not NPE.
    assertNull(FacetValue.forEnum(Issue.class, "SOME_OBSOLETE_ENUM_VALUE", 3));
  }

  @Test
  public void testForEnumBadRankOrdinalReturnsNull() {
    assertNull(FacetValue.forEnum(Rank.class, "999999", 3));
    assertNull(FacetValue.forEnum(Rank.class, "-1", 3));
    assertNull(FacetValue.forEnum(Rank.class, "not-a-number", 3));
  }

}
