package life.catalogue.parser;

import life.catalogue.api.vocab.area.Country;

import java.util.List;

import org.junit.Test;

import com.google.common.collect.Lists;

/**
 *
 */
public class CountryParserTest extends ParserTestBase<Country> {

  public CountryParserTest() {
    super(CountryParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(Country.GERMANY, "deu");
    assertParse(Country.GERMANY, "deutschland");
    assertParse(Country.GERMANY, "GER");
    assertParse(Country.GERMANY, "276");
    assertParse(Country.KOSOVO, "Kosovo");
    assertParse(Country.UNITED_KINGDOM, "Great Britain");
    assertParse(Country.CANADA, "Canada");
    assertParse(Country.UNITED_STATES, "United States");
    assertParse(Country.MEXICO, "Mexico");
    assertParse(Country.BELIZE, "Belize");
    assertParse(Country.HONDURAS, "Honduras");
    assertParse(Country.GUATEMALA, "Guatemala");
    assertParse(Country.EL_SALVADOR, "El Salvador");
    assertParse(Country.COSTA_RICA, "Costa Rica");
    // names the enum used before following GBIF's titles, still common in data
    assertParse(Country.UNITED_KINGDOM, "United Kingdom");
    assertParse(Country.CZECH_REPUBLIC, "Czech Republic");
    assertParse(Country.CONGO, "Congo, Republic of the");
    assertParse(Country.CONGO, "Republic of the Congo");
    assertParse(Country.CAPE_VERDE, "Cape Verde");
    assertParse(Country.TAIWAN, "Taiwan");
    assertParse(Country.PALESTINIAN_TERRITORY, "Palestine, State of");
    assertParse(Country.UNITED_KINGDOM, "United Kingdom of Great Britain and Northern Ireland");
    assertParse(Country.CZECH_REPUBLIC, "Czechia");
    assertParse(Country.TAIWAN, "Chinese Taipei");
    assertParse(Country.NAURU, "Nauru");
    assertParse(Country.NAURU, "Naoero");
    // comma or brackets, straight or curly apostrophes
    assertParse(Country.TANZANIA, "Tanzania, United Republic of");
    assertParse(Country.TANZANIA, "Tanzania (United Republic of)");
    assertParse(Country.MOLDOVA, "Moldova, Republic of");
    assertParse(Country.NETHERLANDS, "Netherlands");
    assertParse(Country.NETHERLANDS, "Netherlands (Kingdom of the)");
    assertParse(Country.CÔTE_DIVOIRE, "Côte d’Ivoire");
    assertParse(Country.KOREA_NORTH, "Korea, Democratic People’s Republic of");
    // user defined codes
    assertParse(Country.INTERNATIONAL_WATERS, "xz");

    // other user defined codes
    assertUnparsable("Oceania");
    assertUnparsable("QO");
    assertUnparsable("AA");
    assertUnparsable("XAZ");
    assertUnparsable("unknown");
  }

  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("term", "deuter", "unknown");
  }
}