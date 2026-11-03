package life.catalogue.assembly;

import life.catalogue.api.model.Name;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HierarchySyncTest {

  @Test
  public void parentNameMismatch() {
    Name pagurus = uninomial("Pagurus", Rank.GENUS);
    Name eupagurusBernhardus = name("Eupagurus", "bernhardus", null, Rank.SPECIES);
    Name pagurusBernhardus = name("Pagurus", "bernhardus", null, Rank.SPECIES);
    assertTrue(HierarchySync.parentNameMismatch(eupagurusBernhardus, pagurus));
    assertFalse(HierarchySync.parentNameMismatch(pagurusBernhardus, pagurus));

    // infraspecific names compare with a species parent, and their genus with a genus parent
    Name apisMellifera = name("Apis", "mellifera", null, Rank.SPECIES);
    Name carpatica = name("Apis", "mellifica", "carpatica", Rank.SUBSPECIES);
    Name carnica = name("Apis", "mellifera", "carnica", Rank.SUBSPECIES);
    assertTrue(HierarchySync.parentNameMismatch(carpatica, apisMellifera));
    assertFalse(HierarchySync.parentNameMismatch(carnica, apisMellifera));
    assertFalse(HierarchySync.parentNameMismatch(carpatica, uninomial("Apis", Rank.GENUS)));
    assertTrue(HierarchySync.parentNameMismatch(carpatica, uninomial("Bombus", Rank.GENUS)));

    // other parents are not part of the name
    assertFalse(HierarchySync.parentNameMismatch(eupagurusBernhardus, uninomial("Paguridae", Rank.FAMILY)));
    assertFalse(HierarchySync.parentNameMismatch(uninomial("Collembola", Rank.ORDER), uninomial("Protura", Rank.CLASS)));
    // a species under a species is not judged
    assertFalse(HierarchySync.parentNameMismatch(eupagurusBernhardus, pagurusBernhardus));
  }

  private static Name uninomial(String uninomial, Rank rank) {
    Name n = new Name();
    n.setUninomial(uninomial);
    n.setRank(rank);
    n.rebuildScientificName();
    return n;
  }

  private static Name name(String genus, String epithet, String infraEpithet, Rank rank) {
    Name n = new Name();
    n.setGenus(genus);
    n.setSpecificEpithet(epithet);
    n.setInfraspecificEpithet(infraEpithet);
    n.setRank(rank);
    n.rebuildScientificName();
    return n;
  }
}
