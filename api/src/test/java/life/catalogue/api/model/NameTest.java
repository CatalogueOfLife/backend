package life.catalogue.api.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.jackson.SerdeTestBase;
import org.gbif.nameparser.api.*;
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
    Name n = TestEntityGenerator.newName();
    // being ignored in json
    n.setNameIndexId(null);
    return n;
  }
  
  @Test
  public void equalsNull() throws Exception {
    Name n1 = new Name();
    Name n2 = new Name();
    assertEquals(n1, n2);
    
    n1.setRank(null);
    n2.setRank(null);
    assertEquals(n1, n2);
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
    assertEquals("Abies × alba ssp.", n.canonicalNameWithAuthorship());
    
    n.setInfraspecificEpithet("alpina");
    n.setCombinationAuthorship(Authorship.yearAuthors("1999", "L.","DC."));
    n.setBasionymAuthorship(Authorship.yearAuthors("1899","Lin.","Deca."));
    assertEquals("Abies × alba subsp. alpina (Lin. & Deca., 1899) L. & DC., 1999", n.canonicalNameWithAuthorship());
    
    n.setRemarks("nom.illeg.");
    assertEquals("Abies × alba subsp. alpina (Lin. & Deca., 1899) L. & DC., 1999", n.canonicalNameWithAuthorship());
    assertEquals("Abies × alba subsp. alpina (Lin. & Deca., 1899) L. & DC., 1999", n.canonicalNameWithAuthorship());
    assertEquals("Abies × alba subsp. alpina", n.canonicalNameWithoutAuthorship());
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
    assertEquals("Spirulina subsalsa subsalsa", n.canonicalNameWithAuthorship());
    n.setCode(NomCode.BACTERIAL);
    assertEquals("Spirulina subsalsa subsalsa", n.canonicalNameWithAuthorship());
  }
}