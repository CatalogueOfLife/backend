package life.catalogue.api.datapackage;

import org.junit.Test;

import static org.junit.Assert.*;

public class ColTermTest {
  
  @Test
  public void isClass() {
    for (ColdpTerm t : ColdpTerm.values()) {
      if (t.isClass()) {
        assertTrue(ColdpTerm.RESOURCES.containsKey(t));
      } else {
        assertFalse(ColdpTerm.RESOURCES.containsKey(t));
      }
    }
  }
  
  @Test
  public void find() {
    assertEquals(ColdpTerm.Taxon, ColdpTerm.find("taxon ", true));
    assertEquals(ColdpTerm.VernacularName, ColdpTerm.find("Vernacular-name", true));
    assertEquals(ColdpTerm.Description, ColdpTerm.find("description ", true));
    assertEquals(ColdpTerm.description, ColdpTerm.find("des_cription ", false));
  }
  
  @Test
  public void higherRanks() {
    for (ColdpTerm t : ColdpTerm.HIGHER_RANKS) {
      assertFalse(t.isClass());
      assertTrue(ColdpTerm.RESOURCES.get(ColdpTerm.Taxon).contains(t));
    }
  }
}