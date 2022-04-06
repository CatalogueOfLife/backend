package life.catalogue.matching;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class NameIndexImplTest {

  @Test
  public void normalizeCanonicalRank() {
    assertEquals(Rank.SUPRAGENERIC_NAME, NameIndexImpl.normCanonicalRank(Rank.KINGDOM));
    assertEquals(Rank.SUPRAGENERIC_NAME, NameIndexImpl.normCanonicalRank(Rank.ORDER));
    assertEquals(Rank.SUPRAGENERIC_NAME, NameIndexImpl.normCanonicalRank(Rank.PARVCLASS));
    assertEquals(Rank.SUPRAGENERIC_NAME, NameIndexImpl.normCanonicalRank(Rank.SUBCLASS));
    assertEquals(Rank.SUPRAGENERIC_NAME, NameIndexImpl.normCanonicalRank(Rank.SUPRAGENERIC_NAME));

    assertEquals(Rank.FAMILY, NameIndexImpl.normCanonicalRank(Rank.FAMILY));
    assertEquals(Rank.FAMILY, NameIndexImpl.normCanonicalRank(Rank.SUPERFAMILY));
    assertEquals(Rank.FAMILY, NameIndexImpl.normCanonicalRank(Rank.INFRAFAMILY));
    assertEquals(Rank.FAMILY, NameIndexImpl.normCanonicalRank(Rank.TRIBE));

    assertEquals(Rank.GENUS, NameIndexImpl.normCanonicalRank(Rank.GENUS));
    assertEquals(Rank.GENUS, NameIndexImpl.normCanonicalRank(Rank.SUBGENUS));
    assertEquals(Rank.GENUS, NameIndexImpl.normCanonicalRank(Rank.SECTION));
    assertEquals(Rank.GENUS, NameIndexImpl.normCanonicalRank(Rank.SERIES));

    assertEquals(Rank.SPECIES, NameIndexImpl.normCanonicalRank(Rank.SPECIES));
    assertEquals(Rank.SPECIES, NameIndexImpl.normCanonicalRank(Rank.SPECIES_AGGREGATE));

    assertEquals(Rank.SUBSPECIES, NameIndexImpl.normCanonicalRank(Rank.SUBSPECIES));
    assertEquals(Rank.SUBSPECIES, NameIndexImpl.normCanonicalRank(Rank.INFRASPECIFIC_NAME));
    assertEquals(Rank.SUBSPECIES, NameIndexImpl.normCanonicalRank(Rank.CULTIVAR_GROUP));
    assertEquals(Rank.SUBSPECIES, NameIndexImpl.normCanonicalRank(Rank.VARIETY));
    assertEquals(Rank.SUBSPECIES, NameIndexImpl.normCanonicalRank(Rank.FORM));
    assertEquals(Rank.SUBSPECIES, NameIndexImpl.normCanonicalRank(Rank.SUBFORM));
    assertEquals(Rank.SUBSPECIES, NameIndexImpl.normCanonicalRank(Rank.SUBVARIETY));
    assertEquals(Rank.SUBSPECIES, NameIndexImpl.normCanonicalRank(Rank.CULTIVAR));
    assertEquals(Rank.SUBSPECIES, NameIndexImpl.normCanonicalRank(Rank.FORMA_SPECIALIS));

    assertEquals(Rank.UNRANKED, NameIndexImpl.normCanonicalRank(Rank.UNRANKED));
    assertEquals(Rank.UNRANKED, NameIndexImpl.normCanonicalRank(Rank.OTHER));
    assertEquals(Rank.UNRANKED, NameIndexImpl.normCanonicalRank(null));

    // make sure we never get an IAE
    for (Rank r : Rank.values()) {
      assertNotNull(NameIndexImpl.normCanonicalRank(r));
    }
  }

  @Test
  public void normalizeRank() {
    assertEquals(Rank.ORDER, NameIndexImpl.normRank(Rank.ORDER));
    assertEquals(Rank.PARVCLASS, NameIndexImpl.normRank(Rank.PARVCLASS));
    assertEquals(Rank.FAMILY, NameIndexImpl.normRank(Rank.FAMILY));
    assertEquals(Rank.SUPERFAMILY, NameIndexImpl.normRank(Rank.SUPERFAMILY));
    assertEquals(Rank.INFRAFAMILY, NameIndexImpl.normRank(Rank.INFRAFAMILY));
    assertEquals(Rank.TRIBE, NameIndexImpl.normRank(Rank.TRIBE));
    assertEquals(Rank.SUBGENUS, NameIndexImpl.normRank(Rank.SUBGENUS));
    assertEquals(Rank.SECTION, NameIndexImpl.normRank(Rank.SECTION));
    assertEquals(Rank.SERIES, NameIndexImpl.normRank(Rank.SERIES));
    assertEquals(Rank.SPECIES, NameIndexImpl.normRank(Rank.SPECIES));
    assertEquals(Rank.SPECIES_AGGREGATE, NameIndexImpl.normRank(Rank.SPECIES_AGGREGATE));
    assertEquals(Rank.SUBSPECIES, NameIndexImpl.normRank(Rank.SUBSPECIES));
    assertEquals(Rank.CULTIVAR_GROUP, NameIndexImpl.normRank(Rank.CULTIVAR_GROUP));
    assertEquals(Rank.VARIETY, NameIndexImpl.normRank(Rank.VARIETY));
    assertEquals(Rank.FORM, NameIndexImpl.normRank(Rank.FORM));
    assertEquals(Rank.SUBFORM, NameIndexImpl.normRank(Rank.SUBFORM));
    assertEquals(Rank.SUBVARIETY, NameIndexImpl.normRank(Rank.SUBVARIETY));
    assertEquals(Rank.CULTIVAR, NameIndexImpl.normRank(Rank.CULTIVAR));
    assertEquals(Rank.FORMA_SPECIALIS, NameIndexImpl.normRank(Rank.FORMA_SPECIALIS));

    assertEquals(Rank.UNRANKED, NameIndexImpl.normRank(Rank.SUPRAGENERIC_NAME));
    assertEquals(Rank.UNRANKED, NameIndexImpl.normRank(Rank.INFRAGENERIC_NAME));
    assertEquals(Rank.UNRANKED, NameIndexImpl.normRank(Rank.INFRASPECIFIC_NAME));
    assertEquals(Rank.UNRANKED, NameIndexImpl.normRank(Rank.UNRANKED));
    assertEquals(Rank.UNRANKED, NameIndexImpl.normRank(Rank.OTHER));
    assertEquals(Rank.UNRANKED, NameIndexImpl.normRank(null));

    // make sure we never get an IAE
    for (Rank r : Rank.values()) {
      assertNotNull(NameIndexImpl.normRank(r));
    }
  }

}