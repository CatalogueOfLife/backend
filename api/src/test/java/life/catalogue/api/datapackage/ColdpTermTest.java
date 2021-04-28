package life.catalogue.api.datapackage;

import org.junit.Test;
import static life.catalogue.api.datapackage.ColdpTerm.*;
import static org.junit.Assert.assertTrue;

public class ColdpTermTest {

  @Test
  public void testNameUsage(){
    for (ColdpTerm t : RESOURCES.get(Name)) {
      if (t == genus) continue;
      assertTrue(RESOURCES.get(NameUsage).contains(t));
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