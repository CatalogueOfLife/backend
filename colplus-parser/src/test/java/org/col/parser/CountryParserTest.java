package org.col.parser;

import com.google.common.collect.Lists;
import org.col.api.vocab.Country;
import org.junit.Test;

import java.util.List;

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

    assertUnparsable("unknown");
    assertUnparsable("zz");
  }

  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("term", "deuter", "unknown");
  }
}