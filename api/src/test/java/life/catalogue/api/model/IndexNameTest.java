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
  public void infrageneric() {
    Name n = new Name();
    n.setRank(Rank.SUBGENUS);
    n.setScientificName("Abies (Paxus)");
    n.setGenus("Abies");
    n.setInfragenericEpithet("Paxus");

    IndexName in = new IndexName(n);
    assertEquals("Abies (Paxus)", in.getScientificName());
    assertEquals("Abies", in.getGenus());
    assertEquals("Paxus", in.getInfragenericEpithet());

    // we remove the subgenus from binomials
    n = new Name();
    n.setRank(Rank.SPECIES);
    n.setScientificName("Abies (Paxus) petruska");
    n.setGenus("Abies");
    n.setInfragenericEpithet("Paxus");
    n.setSpecificEpithet("petruska");

    in = new IndexName(n);
    assertEquals("Abies petruska", in.getScientificName());
    assertEquals("Abies", in.getGenus());
    assertNull(in.getInfragenericEpithet());
    assertEquals("petruska", in.getSpecificEpithet());
  }

}