package org.col.api;

import org.gbif.nameparser.api.NamePart;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class NameTest {

  @Test
  public void isConsistent() throws Exception {
    Name n = new Name();
    assertTrue(n.isConsistent());

    n.setUninomial("Asteraceae");
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

    n.setUninomial("Abies");
    assertTrue(n.isConsistent());

    n.getCombinationAuthorship().getAuthors().add("Mill.");
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

  @Test
  public void conversionAndFormatting() throws Exception {
    Name n = new Name();
    n.setGenus("Abies");
    n.setSpecificEpithet("alba");
    n.setNotho(NamePart.SPECIFIC);
    n.setRank(Rank.SUBSPECIES);
    assertEquals("Abies × alba subsp.", n.canonicalNameComplete());

    n.setInfraspecificEpithet("alpina");
    n.getCombinationAuthorship().setYear("1999");
    n.getCombinationAuthorship().getAuthors().add("L.");
    n.getCombinationAuthorship().getAuthors().add("DC.");
    n.getBasionymAuthorship().setYear("1899");
    n.getBasionymAuthorship().getAuthors().add("Lin.");
    n.getBasionymAuthorship().getAuthors().add("Deca.");
    assertEquals("Abies × alba subsp. alpina (Lin. & Deca., 1899) L. & DC., 1999", n.canonicalNameComplete());
  }
}