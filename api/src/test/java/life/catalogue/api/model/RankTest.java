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
    // name-parser 3.16: +3 to all ranks at/after FAMILY from the inserted zoological series ranks
    // name-parser 4.2: +1 to all ranks after INFRAGENUS from the inserted botanical DIVISION_BOTANY
    assertEquals(0, Rank.SUPERDOMAIN.ordinal());
    assertEquals(8, Rank.KINGDOM.ordinal());
    assertEquals(64, Rank.FAMILY.ordinal());
    assertEquals(73, Rank.GENUS.ordinal());
    assertEquals(85, Rank.SPECIES.ordinal());
    assertEquals(89, Rank.SUBSPECIES.ordinal());
    assertEquals(116, Rank.UNRANKED.ordinal());
    assertEquals(Rank.UNRANKED.ordinal()+1, Rank.values().length);
  }
}