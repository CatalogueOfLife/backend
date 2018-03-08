package org.col.api;

import org.col.api.model.Name;
import org.gbif.nameparser.api.NamePart;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class NameTest {

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