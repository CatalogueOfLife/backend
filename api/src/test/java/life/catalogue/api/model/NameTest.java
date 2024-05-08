package life.catalogue.api.model;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.jackson.SerdeTestBase;
import life.catalogue.api.vocab.MatchType;

import org.gbif.nameparser.api.*;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import static org.junit.Assert.*;

/**
 *
 */
public class NameTest extends SerdeTestBase<Name> {
  
  public NameTest() {
    super(Name.class);
  }

  static public void assertEqualsWithoutNidx(Name expected, Name actual){
    clearNidx(expected);
    clearNidx(actual);
    assertEquals(expected, actual);
  }

  static void clearNidx(Name n) {
    n.setNamesIndexType(null);
    n.setNamesIndexId(null);
  }

  @Override
  public Name genTestValue() throws Exception {
    Name n = TestEntityGenerator.newName();
    return n;
  }

  /**
   * https://github.com/CatalogueOfLife/backend/issues/1229
   */
  @Test
  public void squarebracketAuthors() throws Exception {
    Name n = new Name();
    n.setGenus("Toleria");
    n.setSpecificEpithet("aegerides");
    n.setBasionymAuthorship(ExAuthorship.yearAuthors("[1916]", "Strand"));
    n.setRank(Rank.SPECIES);
    n.rebuildAuthorship();
    n.rebuildScientificName();
    assertEquals("Toleria aegerides (Strand, [1916])", n.getLabel());
  }

  /**
   * https://github.com/CatalogueOfLife/checklistbank/issues/1122
   */
  @Test
  public void subgenericHtml() throws Exception {
    Name n = new Name();
    n.setGenus("Acritus");
    n.setRank(Rank.GENUS);
    n.rebuildScientificName();
    assertEquals("Acritus", n.getLabel());
    assertEquals("<i>Acritus</i>", n.getLabelHtml());

    n.setInfragenericEpithet("Acritus");
    n.setRank(Rank.SUBGENUS);
    n.rebuildScientificName();
    assertEquals("Acritus (Acritus)", n.getLabel());
    assertEquals("<i>Acritus (Acritus)</i>", n.getLabelHtml());

    n.setGenus(null);
    n.rebuildScientificName();
    assertEquals("Acritus", n.getLabel());
    assertEquals("<i>Acritus</i>", n.getLabelHtml());

    n.setGenus("Acritus");
    n.setSpecificEpithet("fidjiensis");
    n.rebuildScientificName();
    assertEquals("Acritus (Acritus) fidjiensis", n.getLabel());
    assertEquals("<i>Acritus (Acritus) fidjiensis</i>", n.getLabelHtml());
  }

  @Test
  public void copy() throws Exception {
    Name n1 = TestEntityGenerator.newName();
    n1.setOriginalSpelling(true);
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
  public void isIndetermined() throws Exception {
    Name n = new Name();
    assertFalse(n.isIndetermined());
    //TODO: add more tests at various ranks incl infragenerics
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
    n.setCombinationAuthorship(ExAuthorship.yearAuthors("1999", "L.","DC."));
    n.setBasionymAuthorship(ExAuthorship.yearAuthors("1899","Lin.","Deca."));
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
    n.setCombinationAuthorship(ExAuthorship.yearAuthors("1999", "L.","DC."));
    n.setBasionymAuthorship(ExAuthorship.yearAuthors("1899","Lin.","Deca."));
    n.setNomenclaturalNote("nom.illeg.");
    n.rebuildScientificName();
    n.rebuildAuthorship();

    assertEquals("Abies × alba subsp. alpina (Lin. & Deca., 1899) L. & DC., 1999 nom.illeg.", n.getLabel());
    assertEquals("<i>Abies × alba</i> subsp. <i>alpina</i> (Lin. & Deca., 1899) L. & DC., 1999 nom.illeg.", n.getLabelHtml());

    // https://github.com/CatalogueOfLife/backend/issues/1090
    n = new Name();
    n.setGenus("Hieracium");
    n.setSpecificEpithet("brevifolium");
    n.setRank(Rank.SPECIES);
    n.rebuildScientificName();
    n.rebuildAuthorship();
    assertEquals("Hieracium brevifolium", n.getLabel());
    assertEquals("<i>Hieracium brevifolium</i>", n.getLabelHtml());

    n.setRank(Rank.SUBSPECIES);
    n.setInfraspecificEpithet("malyi-caroli");
    n.rebuildScientificName();
    n.rebuildAuthorship();
    assertEquals("Hieracium brevifolium malyi-caroli", n.getLabel());
    assertEquals("<i>Hieracium brevifolium malyi-caroli</i>", n.getLabelHtml());

    n.setCombinationAuthorship(ExAuthorship.yearAuthors(null, "Zahn"));
    n.setBasionymAuthorship(ExAuthorship.yearAuthors(null,"Gus. Schneid."));
    n.setCode(NomCode.BOTANICAL);

    n.rebuildScientificName();
    n.rebuildAuthorship();
    assertEquals("Hieracium brevifolium subsp. malyi-caroli (Gus. Schneid.) Zahn", n.getLabel());
    assertEquals("<i>Hieracium brevifolium</i> subsp. <i>malyi-caroli</i> (Gus. Schneid.) Zahn", n.getLabelHtml());

    var jsonN = ApiModule.MAPPER.writeValueAsString(n);
    System.out.println(jsonN);
    assertEquals(2, StringUtils.countMatches(jsonN, "\"label"));

    var u = new BareName(n);
    var jsonU = ApiModule.MAPPER.writeValueAsString(u);
    System.out.println(jsonU);
    assertEquals(2, StringUtils.countMatches(jsonU, "\"label"));

    n.setOriginalSpelling(true);
    assertEquals("Hieracium brevifolium subsp. malyi-caroli [sic] (Gus. Schneid.) Zahn", n.getLabel());
    assertEquals("<i>Hieracium brevifolium</i> subsp. <i>malyi-caroli</i> [sic] (Gus. Schneid.) Zahn", n.getLabelHtml());

    n.setOriginalSpelling(false);
    assertEquals("Hieracium brevifolium subsp. malyi-caroli corrig. (Gus. Schneid.) Zahn", n.getLabel());
    assertEquals("<i>Hieracium brevifolium</i> subsp. <i>malyi-caroli</i> corrig. (Gus. Schneid.) Zahn", n.getLabelHtml());
  }

  @Test
  public void scientificNameHtml() throws Exception {
    Name n = new Name();
    n.setType(NameType.SCIENTIFIC);
    n.setRank(Rank.SPECIES);
    n.setGenus("Abies");
    n.setScientificName("Abies alba");
    assertEquals("<i>Abies alba</i>", n.getLabelHtml());

    n.setRank(Rank.SUBSPECIES);
    n.setScientificName("Abies alba subsp. montana");
    assertEquals("<i>Abies alba</i> subsp. <i>montana</i>", n.getLabelHtml());

    n.setScientificName("Abies alba nothosubsp. montana");
    assertEquals("<i>Abies alba</i> nothosubsp. <i>montana</i>", n.getLabelHtml());

    n.setScientificName("Abies × alba subsp. montana");
    assertEquals("<i>Abies × alba</i> subsp. <i>montana</i>", n.getLabelHtml());

    n.setRank(Rank.GENUS);
    n.setScientificName(n.getGenus());
    assertEquals("<i>Abies</i>", n.getLabelHtml());

    n.setRank(Rank.FAMILY);
    n.setScientificName("Pinaceae");
    assertEquals("Pinaceae", n.getLabelHtml());

    for (Rank r : Rank.values()) {
      if (r.isSpeciesOrBelow() && r.getMarker() != null) {
        n.setRank(r);
        n.setScientificName("Abies alba "+r.getMarker()+" montana");

        System.out.printf("%s -> %s%n", r, n.getLabelHtml());
        assertEquals("<i>Abies alba</i> "+r.getMarker()+" <i>montana</i>", n.getLabelHtml());
      }
    }

    n = new Name();
    n.setRank(Rank.SPECIES);
    n.setScientificName("Abutilon yellows virus ICTV");
    n.setType(NameType.VIRUS);
    assertEquals("Abutilon yellows virus ICTV", n.getLabelHtml());
  }

}