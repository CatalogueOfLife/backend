package life.catalogue.api.model;

import life.catalogue.coldp.ColdpTerm;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static org.junit.Assert.*;

public class ClassificationTest {
  
  @Test
  public void equalsAboveRank() {
    Classification c1 = new Classification();
    Classification c2 = new Classification();
    
    assertTrue(c1.equals(c2));
    assertTrue(c1.equalsAboveRank(c2, Rank.FAMILY));
    
    c1.setGenus("Abies");
    assertTrue(c1.equalsAboveRank(c2, Rank.FAMILY));
    assertTrue(c1.equalsAboveRank(c2, Rank.GENUS));
    assertFalse(c1.equalsAboveRank(c2, Rank.SUBGENUS));
    
    c2.setFamily("Pinaceae");
    assertTrue(c1.equalsAboveRank(c2, Rank.ORDER));
    assertTrue(c1.equalsAboveRank(c2, Rank.FAMILY));
    assertFalse(c1.equalsAboveRank(c2, Rank.GENUS));
    
    c1.setFamily("Pinaceae");
    assertTrue(c1.equalsAboveRank(c2, Rank.GENUS));
    
    c1.setKingdom("Plantae");
    c2.setKingdom(c1.getKingdom());
    c1.setPhylum("Phylo");
    c2.setPhylum(c1.getPhylum());
    assertTrue(c1.equalsAboveRank(c2, Rank.GENUS));
    assertFalse(c1.equalsAboveRank(c2, Rank.SUBGENUS));
  }
  
  @Test
  public void higherRanks() {
    Classification c = new Classification();
    int hash = c.hashCode();
    for (ColdpTerm t : ColdpTerm.DENORMALIZED_RANKS) {
      assertTrue(c.setByTerm(t, "xyz"));
      assertNotEquals(hash, c.hashCode());
      hash = c.hashCode();
    }
  }
  
}