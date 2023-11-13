package life.catalogue.coldp;

import java.util.HashSet;

import org.junit.Test;

import static life.catalogue.coldp.ColdpTerm.*;
import static org.junit.Assert.*;

public class ColdpTermTest {

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
  public void remarks() {
    for (ColdpTerm cl : ColdpTerm.RESOURCES.keySet()) {
      if (cl != Treatment) {
        assertTrue(cl + " has no remarks", ColdpTerm.RESOURCES.get(cl).contains(remarks));
      }
    }
  }

  @Test
  public void resourceTermsUnique() {
    for (ColdpTerm rt : ColdpTerm.RESOURCES.keySet()) {
      assertEquals("Duplicate terms in "+rt, new HashSet<>(ColdpTerm.RESOURCES.get(rt)).size(), ColdpTerm.RESOURCES.get(rt).size());
    }
  }

  @Test
  public void find() {
    assertEquals(Taxon, ColdpTerm.find("taxon ", true));
    assertEquals(VernacularName, ColdpTerm.find("Vernacular-name", true));
    assertEquals(Treatment, ColdpTerm.find("treatment ", true));
    assertEquals(document, ColdpTerm.find("doc_ument ", false));
  }

  @Test
  public void higherRanks() {
    for (ColdpTerm t : DENORMALIZED_RANKS) {
      assertFalse(t.isClass());
      assertTrue(RESOURCES.get(ColdpTerm.Taxon).contains(t));
    }
  }

  @Test
  public void testNameUsage(){
    for (ColdpTerm t : RESOURCES.get(Name)) {
      if (t == genus) continue;
      assertTrue(t + " missing in NameUsage", RESOURCES.get(NameUsage).contains(t));
    }
    for (ColdpTerm t : RESOURCES.get(Taxon)) {
      if (t == provisional || t == nameID) continue;
      assertTrue(RESOURCES.get(NameUsage).contains(t));
    }
    for (ColdpTerm t : RESOURCES.get(Synonym)) {
      if (t == nameID || t == taxonID) continue;
      assertTrue(RESOURCES.get(NameUsage).contains(t));
    }
    // each term exists in at least one resource
    for (ColdpTerm t : ColdpTerm.values()) {
      if (t.isClass()) {
        assertTrue(t + " is no resource", RESOURCES.containsKey(t));

      } else {
        boolean found = false;
        for (var res : RESOURCES.values()) {
          if (res.contains(t)) {
            found = true;
            break;
          }
        }
        assertTrue(t + " without resource", found);
      }
    }

  }
}