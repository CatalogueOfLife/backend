package life.catalogue.api.model;

import org.gbif.nameparser.api.Rank;

import org.junit.Ignore;
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
  @Ignore
  public void warnOnOrdinalChange() {
    assertEquals(105, Rank.values().length);
    assertEquals(5, Rank.KINGDOM.ordinal());
    assertEquals(53, Rank.FAMILY.ordinal());
    assertEquals(62, Rank.GENUS.ordinal());
    assertEquals(73, Rank.SPECIES.ordinal());
    assertEquals(77, Rank.SUBSPECIES.ordinal());
    assertEquals(104, Rank.UNRANKED.ordinal());
  }
}