package life.catalogue.api.model;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RankTest {

  /**
   * Makes sure the rank enumeration maintained in the name parser project did not change.
   * The int ordinals are used in various persistency layer, so after a rank enum change we need to:
   *  - rebuild the (mapdb) names index
   *  - rebuild the ES search index
   */
  @Test
  public void warnOnOrdinalChange() {
    assertEquals(97, Rank.values().length);
    assertEquals(4, Rank.KINGDOM.ordinal());
    assertEquals(51, Rank.FAMILY.ordinal());
    assertEquals(59, Rank.GENUS.ordinal());
    assertEquals(70, Rank.SPECIES.ordinal());
    assertEquals(73, Rank.SUBSPECIES.ordinal());
    assertEquals(96, Rank.UNRANKED.ordinal());
  }
}