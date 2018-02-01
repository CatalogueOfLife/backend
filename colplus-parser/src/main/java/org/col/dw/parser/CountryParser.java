package org.col.dw.parser;

import org.col.dw.api.vocab.Country;
import org.col.dw.api.vocab.VocabularyUtils;

/**
 * CoL country parser wrapping the GBIF country parser
 */
public class CountryParser extends GbifParserBased<Country, org.gbif.api.vocabulary.Country> {
  public static final Parser<Country> PARSER = new CountryParser();

  public CountryParser() {
    super(Country.class, org.gbif.common.parsers.CountryParser.getInstance());
  }

  @Override
  Country convertFromGbif(org.gbif.api.vocabulary.Country value) {
    return VocabularyUtils.convertEnum(Country.class, value);
  }

}
