package org.col.api.datapackage;

import org.junit.Test;

import static org.junit.Assert.*;

public class ColTermTest {
  
  @Test
  public void isClass() {
    for (ColTerm t : ColTerm.values()) {
      if (t.isClass()) {
        assertTrue(ColTerm.RESOURCES.containsKey(t));
      } else {
        assertFalse(ColTerm.RESOURCES.containsKey(t));
      }
    }
  }
  
  @Test
  public void find() {
    assertEquals(ColTerm.Taxon, ColTerm.find("taxon ", true));
    assertEquals(ColTerm.VernacularName, ColTerm.find("Vernacular-name", true));
    assertEquals(ColTerm.Description, ColTerm.find("description ", true));
    assertEquals(ColTerm.description, ColTerm.find("des_cription ", false));
  }
  
  @Test
  public void higherRanks() {
    for (ColTerm t : ColTerm.HIGHER_RANKS) {
      assertFalse(t.isClass());
      assertTrue(ColTerm.RESOURCES.get(ColTerm.Taxon).contains(t));
    }
  }
}