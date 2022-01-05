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
    assertEquals(91, Rank.values().length);
    assertEquals(4, Rank.KINGDOM.ordinal());
    assertEquals(45, Rank.FAMILY.ordinal());
    assertEquals(53, Rank.GENUS.ordinal());
    assertEquals(64, Rank.SPECIES.ordinal());
    assertEquals(67, Rank.SUBSPECIES.ordinal());
    assertEquals(90, Rank.UNRANKED.ordinal());
  }
}