package life.catalogue.importer.neo.model;

import life.catalogue.api.vocab.NomRelType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class RelTypeTest {

  @Test
  public void testRelTypeCompleteness() {
    for (NomRelType nrt : NomRelType.values()) {
      RelType rt = RelType.from(nrt);
      assertNotNull("Neo4j relation for " + nrt + " missing ", rt);
      assertEquals(nrt, rt.nomRelType);
    }
  }

}