package org.col.parser;

import java.util.Optional;
import java.util.regex.Pattern;

import com.google.common.annotations.VisibleForTesting;
import org.col.api.util.VocabularyUtils;
import org.col.api.vocab.Country;

/**
 * CoL country parser wrapping the GBIF country parser
 */
public class CountryParser extends GbifParserBased<Country, org.gbif.api.vocabulary.Country> {
  public static final Parser<Country> PARSER = new CountryParser();
  public static final Pattern USER_DEF = Pattern.compile("^(AA[A-Z]?|Q[M-Z][A-Z]?|X[A-Z][A-Z]?|ZZ[A-Z]?)$", Pattern.CASE_INSENSITIVE);

  public CountryParser() {
    super(Country.class, org.gbif.common.parsers.CountryParser.getInstance());
  }

  @Override
  public Optional<Country> parse(String value) throws UnparsableException {
    try {
      return super.parse(value);
    } catch (UnparsableException e) {
      if (value.equalsIgnoreCase(Country.INTERNATIONAL_WATERS.getIso2LetterCode()) ||
          value.equalsIgnoreCase(Country.INTERNATIONAL_WATERS.getIso3LetterCode())) {
        return Optional.of(Country.INTERNATIONAL_WATERS);
      }
      // try to parser number codes too
      try {
        int number = Integer.parseInt(value);
        if (number > 0 && number <= 902) {
          for (Country c : Country.values()) {
            if (c.getIsoNumericalCode() != null && c.getIsoNumericalCode().equals(number)) {
              return Optional.of(c);
            }
          }
        }
      } catch (NumberFormatException e1) {
      }
      // no idea...
      throw e;
    }
  }

  @Override
  @VisibleForTesting
  protected Country convertFromGbif(org.gbif.api.vocabulary.Country value) {
    switch (value) {
      case UNKNOWN:
      case USER_DEFINED:
        return null;
      default:
        if (value.getIso2LetterCode() == null) {
          return null;
        }
        return VocabularyUtils.convertEnum(Country.class, value);
    }
  }
}
