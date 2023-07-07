package life.catalogue.api.model;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static org.junit.Assert.*;

public class IndexNameTest {

  @Test
  public void newCanonical() {
    IndexName n = new IndexName();
    n.setScientificName("Abies alba Querlewutz, with some unparsable remarks");
    n.setAuthorship("Querlewutz");
    n.setRank(Rank.SPECIES);
    assertFalse(n.isCanonical()); // purely id based
    assertFalse(n.qualifiesAsCanonical());

    assertEquals(n, new IndexName(n));
    var cn = IndexName.newCanonical(n);
    assertNotEquals(n, IndexName.newCanonical(n));
    assertFalse(cn.hasAuthorship());
    assertTrue(cn.qualifiesAsCanonical());

    n.setAuthorship(null); // canonicals have no authorship and no rank
    n.setRank(Rank.UNRANKED);
    assertEquals(n, new IndexName(n));
    assertEquals(n, IndexName.newCanonical(n));
  }

  @Test
  public void normalizeRank() {
    assertEquals(Rank.ORDER, IndexName.normRank(Rank.ORDER));
    assertEquals(Rank.PARVCLASS, IndexName.normRank(Rank.PARVCLASS));
    assertEquals(Rank.FAMILY, IndexName.normRank(Rank.FAMILY));
    assertEquals(Rank.SUPERFAMILY, IndexName.normRank(Rank.SUPERFAMILY));
    assertEquals(Rank.INFRAFAMILY, IndexName.normRank(Rank.INFRAFAMILY));
    assertEquals(Rank.TRIBE, IndexName.normRank(Rank.TRIBE));
    assertEquals(Rank.SUBGENUS, IndexName.normRank(Rank.SUBGENUS));
    assertEquals(Rank.SECTION, IndexName.normRank(Rank.SECTION));
    assertEquals(Rank.SERIES, IndexName.normRank(Rank.SERIES));
    assertEquals(Rank.SPECIES, IndexName.normRank(Rank.SPECIES));
    assertEquals(Rank.SPECIES_AGGREGATE, IndexName.normRank(Rank.SPECIES_AGGREGATE));
    assertEquals(Rank.SUBSPECIES, IndexName.normRank(Rank.SUBSPECIES));
    assertEquals(Rank.CULTIVAR_GROUP, IndexName.normRank(Rank.CULTIVAR_GROUP));
    assertEquals(Rank.VARIETY, IndexName.normRank(Rank.VARIETY));
    assertEquals(Rank.FORM, IndexName.normRank(Rank.FORM));
    assertEquals(Rank.SUBFORM, IndexName.normRank(Rank.SUBFORM));
    assertEquals(Rank.SUBVARIETY, IndexName.normRank(Rank.SUBVARIETY));
    assertEquals(Rank.CULTIVAR, IndexName.normRank(Rank.CULTIVAR));
    assertEquals(Rank.FORMA_SPECIALIS, IndexName.normRank(Rank.FORMA_SPECIALIS));

    assertEquals(Rank.UNRANKED, IndexName.normRank(Rank.SUPRAGENERIC_NAME));
    assertEquals(Rank.UNRANKED, IndexName.normRank(Rank.INFRAGENERIC_NAME));
    assertEquals(Rank.UNRANKED, IndexName.normRank(Rank.INFRASPECIFIC_NAME));
    assertEquals(Rank.UNRANKED, IndexName.normRank(Rank.UNRANKED));
    assertEquals(Rank.UNRANKED, IndexName.normRank(Rank.OTHER));
    assertEquals(Rank.UNRANKED, IndexName.normRank(null));

    // make sure we never get an IAE
    for (Rank r : Rank.values()) {
      assertNotNull(IndexName.normRank(r));
    }
  }

}