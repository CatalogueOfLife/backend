package org.col.parser;

import java.util.List;

import com.google.common.collect.Lists;
import org.col.api.vocab.Country;
import org.junit.Test;

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

    // user defined codes
    assertParse(Country.INTERNATIONAL_WATERS, "xz");

    // other user defined codes
    assertUnparsable("Oceania");
    assertUnparsable("QO");
    assertUnparsable("AA");
    assertUnparsable("XAZ");
    assertUnparsable("unknown");
  }
  
  @Test
  public void testGbifEnumCoverage() throws Exception {
    for (org.gbif.api.vocabulary.Country c : org.gbif.api.vocabulary.Country.values()) {
      Country c2 = ((CountryParser) parser).convertFromGbif(c);
    }
  }

  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("term", "deuter", "unknown");
  }
}