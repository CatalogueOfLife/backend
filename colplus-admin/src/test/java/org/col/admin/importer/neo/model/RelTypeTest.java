package org.col.admin.importer.neo.model;

import org.col.api.vocab.NomRelType;
import org.junit.Test;

import static org.junit.Assert.*;

public class RelTypeTest {

  @Test
  public void testRelTypeCompleteness() {
    for (NomRelType nrt : NomRelType.values()) {
      boolean exists = false;
      for (RelType rt : RelType.values()) {
        if (rt.nomRelType == nrt) {
          exists = true;
          break;
        }
      }
      assertTrue("Neo4j relation for "+nrt+" missing ", exists);
    }
  }

}