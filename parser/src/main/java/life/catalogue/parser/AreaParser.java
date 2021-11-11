package life.catalogue.parser;

import life.catalogue.api.vocab.*;

import java.util.Optional;
import java.util.regex.Pattern;

import life.catalogue.common.kryo.AreaSerializer;

import com.google.common.base.CharMatcher;

import org.apache.commons.lang3.StringUtils;

/**
 * A parser that tries to extract complex area information (actual area id with gazetteer its based on )
 * from a simple location string.
 */
public class AreaParser extends ParserBase<Area> {
  public static final AreaParser PARSER = new AreaParser();
  private static final Pattern PREFIX = Pattern.compile("^([a-z]+)\\s*:\\s*(.+)?\\s*$", Pattern.CASE_INSENSITIVE);
  private static final String ISO = "iso";

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
        var m = PREFIX.matcher(area);
        if (m.find()) {
          String scheme = m.group(1).toLowerCase();
          String value = m.group(2);
          if (StringUtils.isBlank(value)) {
            return Optional.empty();
          } else if (scheme.equalsIgnoreCase(ISO)) {
            return CountryParser.PARSER.parse(value.trim());
          } else {
            return Optional.ofNullable(AreaSerializer.parse(scheme + ":" + value.trim()));
          }
        } else {
          return Optional.of(new AreaImpl(area));
        }
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
