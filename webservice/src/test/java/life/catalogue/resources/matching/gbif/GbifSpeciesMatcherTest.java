package life.catalogue.resources.matching.gbif;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GbifSpeciesMatcherTest {

  @Test
  public void rankContradictsName() {
    assertTrue(GbifSpeciesMatcher.rankContradictsName("Oryctolagus cuniculus cuniculus", Rank.SPECIES));
    assertFalse(GbifSpeciesMatcher.rankContradictsName("Oryctolagus cuniculus cuniculus", Rank.SUBSPECIES));
    assertTrue(GbifSpeciesMatcher.rankContradictsName("Macropus Shaw, 1790", Rank.SPECIES));
    assertFalse(GbifSpeciesMatcher.rankContradictsName("Macropus Shaw, 1790", Rank.GENUS));
    assertFalse(GbifSpeciesMatcher.rankContradictsName("Pipistrellus kuhlii (Natterer in Kuhl, 1817)", Rank.SPECIES));
    assertTrue(GbifSpeciesMatcher.rankContradictsName("Polygonum aviculare", Rank.SPECIES_AGGREGATE));
    assertFalse(GbifSpeciesMatcher.rankContradictsName("Polygonum aviculare", null));
  }
}
