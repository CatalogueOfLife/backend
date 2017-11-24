package org.col.api;

import org.col.api.vocab.NamePart;
import org.col.api.vocab.Rank;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 */
public class NameTest {

  @Test
  public void buildScientificName() throws Exception {
    Name n = new Name();
    assertNull(n.buildScientificName());

    n.setScientificName("Asteraceae");
    n.setRank(Rank.FAMILY);
    assertEquals("Asteraceae", n.buildScientificName());

    n.setScientificName("Abies");
    n.setRank(Rank.GENUS);
    n.getAuthorship().getAuthors().add("Mill.");
    assertEquals("Abies", n.buildScientificName());

    n.setRank(Rank.UNRANKED);
    assertEquals("Abies", n.buildScientificName());

    n.setInfragenericEpithet("Pinoideae");
    assertEquals("Pinoideae", n.buildScientificName());

    n.setGenus("Abies");
    assertEquals("Abies Pinoideae", n.buildScientificName());

    n.setRank(Rank.SUBGENUS);
    assertEquals("Abies subgen. Pinoideae", n.buildScientificName());

    n.setInfragenericEpithet(null);
    n.setSpecificEpithet("alba");
    assertEquals("Abies alba", n.buildScientificName());

    n.setRank(Rank.SPECIES);
    assertEquals("Abies alba", n.buildScientificName());

    n.setInfraspecificEpithet("alpina");
    assertEquals("Abies alba alpina", n.buildScientificName());

    n.setRank(Rank.SUBSPECIES);
    assertEquals("Abies alba subsp. alpina", n.buildScientificName());

    n.setRank(Rank.VARIETY);
    assertEquals("Abies alba var. alpina", n.buildScientificName());

    n.setRank(Rank.INFRASPECIFIC_NAME);
    assertEquals("Abies alba alpina", n.buildScientificName());

    n.setNotho(NamePart.INFRASPECIFIC);
    assertEquals("Abies alba × alpina", n.buildScientificName());

    n.setNotho(NamePart.GENERIC);
    assertEquals("× Abies alba alpina", n.buildScientificName());
  }

  @Test
  public void testFullAuthorship() throws Exception {
    Name n = new Name();
    assertNull(n.fullAuthorship());

    n.getAuthorship().getAuthors().add("L.");
    assertEquals("L.", n.fullAuthorship());

    n.getBasionymAuthorship().getAuthors().add("Bassier");
    assertEquals("(Bassier) L.", n.fullAuthorship());
    assertEquals("(Bassier) L.", n.fullAuthorship());

    n.getAuthorship().getAuthors().add("Rohe");
    assertEquals("(Bassier) L. & Rohe", n.fullAuthorship());
    assertEquals("(Bassier) L. & Rohe", n.fullAuthorship());

    n.setSanctioningAuthor("Fr.");
    assertEquals("(Bassier) L. & Rohe : Fr.", n.fullAuthorship());


    n = new Name();
    n.getAuthorship().getAuthors().add("L.");
    n.setSanctioningAuthor("Pers.");
    assertEquals("L. : Pers.", n.fullAuthorship());
  }

  @Test
  public void isConsistent() throws Exception {
    Name n = new Name();
    assertTrue(n.isConsistent());

    n.setScientificName("Asteraceae");
    n.setRank(Rank.FAMILY);
    assertTrue(n.isConsistent());
    for (Rank r : Rank.values()) {
      if (r.isSuprageneric()) {
        n.setRank(r);
        assertTrue(n.isConsistent());
      }
    }

    n.setRank(Rank.GENUS);
    assertTrue(n.isConsistent());

    n.setScientificName("Abies");
    assertTrue(n.isConsistent());

    n.getAuthorship().getAuthors().add("Mill.");
    assertTrue(n.isConsistent());

    n.setRank(Rank.SPECIES);
    assertFalse(n.isConsistent());

    n.setInfragenericEpithet("Pinoideae");
    assertFalse(n.isConsistent());

    n.setRank(Rank.SUBGENUS);
    // should we not also check if scientificName property makes sense???
    assertTrue(n.isConsistent());

    n.setGenus("Abies");
    assertTrue(n.isConsistent());

    n.setSpecificEpithet("alba");
    assertFalse(n.isConsistent());

    n.setRank(Rank.SPECIES);
    assertFalse(n.isConsistent());

    n.setInfragenericEpithet(null);
    assertTrue(n.isConsistent());

    n.setRank(Rank.VARIETY);
    assertFalse(n.isConsistent());

    n.setInfraspecificEpithet("alpina");
    assertTrue(n.isConsistent());

    n.setRank(Rank.SPECIES);
    assertFalse(n.isConsistent());

    n.setRank(Rank.UNRANKED);
    assertTrue(n.isConsistent());

    n.setSpecificEpithet(null);
    assertFalse(n.isConsistent());
  }

}