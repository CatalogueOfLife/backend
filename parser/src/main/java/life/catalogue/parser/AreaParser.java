package life.catalogue.parser;

import life.catalogue.api.vocab.*;
import life.catalogue.common.io.TabReader;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import life.catalogue.common.kryo.AreaSerializer;

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
  private static final Pattern EXTENDED_ISO  = Pattern.compile("^iso\\s*:\\s*(" + CountryParser.ISO + ")$", Pattern.CASE_INSENSITIVE);

  public AreaParser() {
    super(Area.class);
  }

  @Override
  public Optional<? extends Area> parse(String area) throws UnparsableException {
    if (area == null || CharMatcher.invisible().and(CharMatcher.whitespace()).matchesAllOf(area)) {
      return Optional.empty();

    } else {
      try {
        // remove invisible
        area = area.trim();
        var m = EXTENDED_ISO.matcher(area);
        if (m.find()) {
          return CountryParser.PARSER.parse(m.group(1));
        }
        return Optional.ofNullable(AreaSerializer.parse(area));
      } catch (IllegalArgumentException e) {
        throw new UnparsableException("Unparsable area " + area, e);
      }
    }
  }

  @Override
  Area parseKnownValues(String upperCaseValue) throws UnparsableException {
    throw new UnsupportedOperationException();
  }

}
