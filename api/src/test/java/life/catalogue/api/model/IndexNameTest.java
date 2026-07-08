package life.catalogue.api.model;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static org.junit.Assert.*;

public class IndexNameTest {

  @Test
  public void newCanonical() {
    // an authored, ranked source name reduces to a rankless, authorless canonical
    Name n = new Name();
    n.setScientificName("Abies alba");
    n.setGenus("Abies");
    n.setSpecificEpithet("alba");
    n.setRank(Rank.SPECIES);
    n.setCombinationAuthorship(Authorship.authors("Querlewutz"));
    n.rebuildAuthorship();

    var cn = IndexName.newCanonical(n);
    assertFalse(cn.hasAuthorship());
    assertTrue(cn.qualifiesAsCanonical());
    assertNull(cn.getAuthorship());
    assertEquals(Rank.UNRANKED, cn.getRank());
    assertEquals("Abies alba", cn.getScientificName());
    assertEquals("Abies", cn.getGenus());
    assertEquals("alba", cn.getSpecificEpithet());

    // an IndexName is single-tier & canonical-only: rankless, authorless by construction
    IndexName idx = new IndexName();
    idx.setScientificName("Abies alba");
    assertNull(idx.getAuthorship());
    assertEquals(Rank.UNRANKED, idx.getRank());
    assertTrue(idx.qualifiesAsCanonical());
    // copying keeps it equal
    assertEquals(idx, new IndexName(idx));
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