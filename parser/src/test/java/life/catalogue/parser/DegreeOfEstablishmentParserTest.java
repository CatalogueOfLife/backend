package life.catalogue.parser;

import life.catalogue.api.vocab.DegreeOfEstablishment;

import java.util.List;

import org.junit.Test;

import com.google.common.collect.Lists;

public class DegreeOfEstablishmentParserTest extends ParserTestBase<DegreeOfEstablishment> {

  public DegreeOfEstablishmentParserTest() {
    super(DegreeOfEstablishmentParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(DegreeOfEstablishment.NATIVE, "native");
    assertParse(DegreeOfEstablishment.INVASIVE, "invasive");
    assertParse(DegreeOfEstablishment.INVASIVE, "Invasive"); // WoRMS Invasiveness vocabulary
    assertParse(DegreeOfEstablishment.WIDESPREAD_INVASIVE, "widespread invasive");
  }

  /**
   * Blackburn et al. category codes, alone or as in GRIIS
   */
  @Test
  public void categories() throws Exception {
    assertParse(DegreeOfEstablishment.ESTABLISHED, "established (category C3)");
    assertParse(DegreeOfEstablishment.INVASIVE, "invasive (category D2)");
    assertParse(DegreeOfEstablishment.WIDESPREAD_INVASIVE, "widespread invasive (category E)");
    assertParse(DegreeOfEstablishment.CAPTIVE, "B1");
    assertParse(DegreeOfEstablishment.CULTIVATED, "b2");
    assertParse(DegreeOfEstablishment.RELEASED, "category B3");
    assertParse(DegreeOfEstablishment.FAILING, "C0");
    assertParse(DegreeOfEstablishment.COLONISING, "colonising (Category D1)");
    assertParse(DegreeOfEstablishment.WIDESPREAD_INVASIVE, "E");
    assertUnparsable("A");
    assertUnparsable("category A");
  }

  /**
   * WoRMS uses its Invasiveness vocabulary for the degree of establishment. Apart from "Invasive" none of its
   * values map to a TDWG degree of establishment, so they must be accepted silently as empty rather than invalid.
   * "NA"/"N/A" are common not-applicable placeholders, e.g. in GRIIS archives.
   * https://github.com/CatalogueOfLife/backend/issues/1511
   */
  @Test
  public void emptyNonDegrees() throws Exception {
    assertEmpty("Not invasive");
    assertEmpty("Of concern");
    assertEmpty("Management recorded");
    assertEmpty("Uncertain");
    assertEmpty("Invasiveness Uncertain");
    assertEmpty("Invasiveness Not specified");
    assertEmpty("NA");
    assertEmpty("N/A");
  }

  @Override
  List<String> unparsableValues() {
    return Lists.newArrayList("a", "pp", "NOMEN", "NOMEN_000029045634");
  }
}
