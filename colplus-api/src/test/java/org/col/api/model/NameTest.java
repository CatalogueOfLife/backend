package org.col.api.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.col.api.TestEntityGenerator;
import org.col.api.jackson.ApiModule;
import org.col.api.jackson.SerdeTestBase;
import org.gbif.nameparser.api.NamePart;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

/**
 *
 */
public class NameTest extends SerdeTestBase<Name> {
  
  public NameTest() {
    super(Name.class);
  }
  
  @Override
  public Name genTestValue() throws Exception {
    return TestEntityGenerator.newName();
  }
  
  @Test
  public void testAuthorship() throws JsonProcessingException {
    Name n = TestEntityGenerator.newName();
    String json = ApiModule.MAPPER.writeValueAsString(n);
    // ignored props
    assertTrue("Missing authorship property: " + json, json.contains("\"authorship\""));
    assertFalse(json.contains("available"));
    assertFalse(json.contains("legitimate"));
  
    n = new Name();
    n.setGenus("Abies");
    n.setSpecificEpithet("alba");
    n.setType(NameType.SCIENTIFIC);
    json = ApiModule.MAPPER.writeValueAsString(n);
    System.out.println(json);
    assertFalse(json.contains("\"combinationAuthorship\""));
    assertFalse(json.contains("\"basionymAuthorship\""));
    assertFalse(json.contains("\"authorship\""));
  }
  
  @Test
  public void conversionAndFormatting() throws Exception {
    Name n = new Name();
    n.setGenus("Abies");
    n.setSpecificEpithet("alba");
    n.setNotho(NamePart.SPECIFIC);
    n.setRank(Rank.SUBSPECIES);
    assertEquals("Abies × alba ssp.", n.canonicalNameComplete());
    
    n.setInfraspecificEpithet("alpina");
    n.getCombinationAuthorship().setYear("1999");
    n.getCombinationAuthorship().getAuthors().add("L.");
    n.getCombinationAuthorship().getAuthors().add("DC.");
    n.getBasionymAuthorship().setYear("1899");
    n.getBasionymAuthorship().getAuthors().add("Lin.");
    n.getBasionymAuthorship().getAuthors().add("Deca.");
    assertEquals("Abies × alba subsp. alpina (Lin. & Deca., 1899) L. & DC., 1999", n.canonicalNameComplete());
  }
  
  /**
   * https://github.com/Sp2000/colplus-backend/issues/478
   */
  @Test
  public void bacterialInfraspec() throws Exception {
    Name n = new Name();
    n.setGenus("Spirulina");
    n.setSpecificEpithet("subsalsa");
    n.setInfraspecificEpithet("subsalsa");
    n.setRank(Rank.INFRASPECIFIC_NAME);
    assertEquals("Spirulina subsalsa subsalsa", n.canonicalNameComplete());
    n.setCode(NomCode.BACTERIAL);
    assertEquals("Spirulina subsalsa subsalsa", n.canonicalNameComplete());
  }
}