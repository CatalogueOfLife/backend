package life.catalogue.api.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.jackson.SerdeTestBase;
import org.gbif.nameparser.api.*;
import org.junit.Test;

import java.util.regex.Matcher;

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
    return n;
  }

  @Test
  public void copy() throws Exception {
    Name n1 = TestEntityGenerator.newName();
    Name n2 = new Name(n1);
    assertEquals(n1, n2);
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
  public void updateNameCacheLabel() throws Exception {
    Name n = new Name();
    n.setGenus("Abies");
    n.setSpecificEpithet("alba");
    n.setNotho(NamePart.SPECIFIC);
    n.setRank(Rank.SUBSPECIES);
    n.rebuildScientificName();
    assertEquals("Abies × alba ssp.", n.getScientificName());
    
    n.setInfraspecificEpithet("alpina");
    n.setCombinationAuthorship(Authorship.yearAuthors("1999", "L.","DC."));
    n.setBasionymAuthorship(Authorship.yearAuthors("1899","Lin.","Deca."));
    n.setNomenclaturalNote("nom.illeg.");
    n.rebuildScientificName();
    n.rebuildAuthorship();
    assertEquals("Abies × alba subsp. alpina", n.getScientificName());
    assertEquals("(Lin. & Deca., 1899) L. & DC., 1999 nom.illeg.", n.getAuthorship());

    // https://github.com/CatalogueOfLife/backend/issues/849#issuecomment-696383826
    n = new Name();
    n.setUninomial("Eucnidoideae");
    n.setRank(Rank.SUPERFAMILY);
    n.rebuildScientificName();
    n.rebuildAuthorship();
    assertEquals("Eucnidoideae", n.getScientificName());

    assertNull(n.getAuthorship());
    assertNull(n.getNomenclaturalNote());

    n.setNomenclaturalNote("ined.");
    n.rebuildScientificName();
    n.rebuildAuthorship();

    assertEquals("Eucnidoideae", n.getScientificName());
    assertEquals("ined.", n.getAuthorship());
    assertEquals("ined.", n.getNomenclaturalNote());
    assertEquals("Eucnidoideae ined.", n.getLabel());
  }

  @Test
  public void label() throws Exception {
    Name n = new Name();
    n.setGenus("Abies");
    n.setSpecificEpithet("alba");
    n.setNotho(NamePart.SPECIFIC);
    n.setRank(Rank.SUBSPECIES);
    n.setInfraspecificEpithet("alpina");
    n.setCombinationAuthorship(Authorship.yearAuthors("1999", "L.","DC."));
    n.setBasionymAuthorship(Authorship.yearAuthors("1899","Lin.","Deca."));
    n.setNomenclaturalNote("nom.illeg.");
    n.rebuildScientificName();
    n.rebuildAuthorship();

    assertEquals("Abies × alba subsp. alpina (Lin. & Deca., 1899) L. & DC., 1999 nom.illeg.", n.getLabel(false));
    assertEquals("<i>Abies × alba</i> subsp. <i>alpina</i> (Lin. & Deca., 1899) L. & DC., 1999 nom.illeg.", n.getLabel(true));
  }

  @Test
  public void linneanPattern() throws Exception {
    Matcher m = Name.LINNEAN_NAME_NO_AUTHOR.matcher("Abies");
    assertTrue(m.find());

    m = Name.LINNEAN_NAME_NO_AUTHOR.matcher("Abies alba");
    assertTrue(m.find());

    m = Name.LINNEAN_NAME_NO_AUTHOR.matcher("Abies alba Mill.");
    assertFalse(m.find());

    m = Name.LINNEAN_NAME_NO_AUTHOR.matcher("Abies DC");
    assertFalse(m.find());

    m = Name.LINNEAN_NAME_NO_AUTHOR.matcher("Abies 4-color");
    assertTrue(m.find());

    m = Name.LINNEAN_NAME_NO_AUTHOR.matcher("Abies alba alpina");
    assertTrue(m.find());

    m = Name.LINNEAN_NAME_NO_AUTHOR.matcher("Abies alba subsp. alpina");
    assertFalse(m.find());

    m = Name.LINNEAN_NAME_NO_AUTHOR.matcher("Abies alba ssp.");
    assertFalse(m.find());
  }

  @Test
  public void scientificNameHtml() throws Exception {
    Name n = new Name();
    n.setType(NameType.SCIENTIFIC);
    n.setRank(Rank.SPECIES);
    n.setGenus("Abies");
    n.setScientificName("Abies alba");
    assertEquals("<i>Abies alba</i>", n.scientificNameHtml());

    n.setRank(Rank.SUBSPECIES);
    n.setScientificName("Abies alba subsp. montana");
    assertEquals("<i>Abies alba</i> subsp. <i>montana</i>", n.scientificNameHtml());

    n.setScientificName("Abies alba nothosubsp. montana");
    assertEquals("<i>Abies alba</i> nothosubsp. <i>montana</i>", n.scientificNameHtml());

    n.setScientificName("Abies × alba subsp. montana");
    assertEquals("<i>Abies × alba</i> subsp. <i>montana</i>", n.scientificNameHtml());

    n.setRank(Rank.GENUS);
    n.setScientificName(n.getGenus());
    assertEquals("<i>Abies</i>", n.scientificNameHtml());

    n.setRank(Rank.FAMILY);
    n.setScientificName("Pinaceae");
    assertEquals("Pinaceae", n.scientificNameHtml());

    for (Rank r : Rank.values()) {
      if (r.isSpeciesOrBelow() && r.getMarker() != null) {
        n.setRank(r);
        n.setScientificName("Abies alba "+r.getMarker()+" montana");
        assertEquals("<i>Abies alba</i> "+r.getMarker()+" <i>montana</i>", n.scientificNameHtml());
      }
    }

    n = new Name();
    n.setRank(Rank.SPECIES);
    n.setScientificName("Abutilon yellows virus ICTV");
    n.setType(NameType.VIRUS);
    assertEquals("Abutilon yellows virus ICTV", n.scientificNameHtml());
  }

}