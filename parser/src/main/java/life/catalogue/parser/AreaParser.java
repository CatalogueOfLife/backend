package life.catalogue.parser;

import life.catalogue.api.vocab.*;
import life.catalogue.common.io.TabReader;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.CharMatcher;
import com.google.common.base.Enums;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * A parser that tries to extract complex area information (actual area id with gazetteer its based on )
 * from a simple location string.
 */
public class AreaParser extends ParserBase<Area> {
  public static final AreaParser PARSER = new AreaParser();

  public AreaParser() {
    super(Area.class);
  }

  @Override
  public Optional<? extends Area> parse(String area) throws UnparsableException {
    if (area == null || CharMatcher.invisible().and(CharMatcher.whitespace()).matchesAllOf(area)) {
      return Optional.empty();

    } else {
      // remove invisible
      String[] parts = CharMatcher.invisible().removeFrom(area).split(":", 2);
      if (parts.length > 1) {
        final Gazetteer standard = GazetteerParser.PARSER.parse(parts[0]).get();
        final String value = parts[1].trim();
        switch (standard) {
          case ISO:
            return CountryParser.PARSER.parse(value);
          case TDWG:
            return Optional.of(TdwgArea.of(value));
          case LONGHURST:
            return Optional.of(LonghurstArea.of(value));
          default:
            // we have not implemented other area enumerations yet!
            return Optional.of(new AreaImpl(standard, parts[1], null));
        }
      } else {
        return Optional.of(new AreaImpl(area));
      }
    }
  }

  @Override
  Area parseKnownValues(String upperCaseValue) throws UnparsableException {
    throw new UnsupportedOperationException();
  }

}
