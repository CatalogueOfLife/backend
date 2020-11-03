package life.catalogue.importer.neo.model;

import life.catalogue.api.vocab.NomRelType;
import life.catalogue.api.vocab.SpeciesInteractionType;
import life.catalogue.api.vocab.TaxonConceptRelType;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

public class RelTypeTest {

  @Test
  @Ignore("Helper util to create mapping, not a real test")
  public void listSpecInter() {
    for (SpeciesInteractionType rt : SpeciesInteractionType.values()) {
      System.out.printf("%s(\"%s\", SpeciesInteractionType.%s),%n", rt, rt.name().toLowerCase(), rt);
    }
  }

  @Test
  public void testRelTypeCompleteness() {
    for (NomRelType nrt : NomRelType.values()) {
      RelType rt = RelType.from(nrt);
      assertNotNull("Neo4j relation for " + nrt + " missing ", rt);
      assertEquals(nrt, rt.nomRelType);
      assertTrue(rt.isNameRel());
      assertFalse(rt.isTaxonConceptRel());
      assertFalse(rt.isSpeciesInteraction());
    }

    for (TaxonConceptRelType nrt : TaxonConceptRelType.values()) {
      RelType rt = RelType.from(nrt);
      assertNotNull("Neo4j relation for " + nrt + " missing ", rt);
      assertEquals(nrt, rt.taxRelType);
      assertFalse(rt.isNameRel());
      assertTrue(rt.isTaxonConceptRel());
      assertFalse(rt.isSpeciesInteraction());
    }

    for (SpeciesInteractionType nrt : SpeciesInteractionType.values()) {
      RelType rt = RelType.from(nrt);
      assertNotNull("Neo4j relation for " + nrt + " missing ", rt);
      assertEquals(nrt, rt.specInterType);
      assertFalse(rt.isNameRel());
      assertFalse(rt.isTaxonConceptRel());
      assertTrue(rt.isSpeciesInteraction());
    }
  }

}