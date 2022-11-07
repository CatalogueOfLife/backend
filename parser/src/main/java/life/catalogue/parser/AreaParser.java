package life.catalogue.parser;

import life.catalogue.api.vocab.Area;
import life.catalogue.api.vocab.AreaImpl;
import life.catalogue.api.vocab.Gazetteer;
import life.catalogue.common.kryo.AreaSerializer;

import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.CharMatcher;

/**
 * A parser that tries to extract complex area information (actual area id with gazetteer its based on )
 * from a simple location string.
 */
public class AreaParser extends ParserBase<Area> {
  public static final AreaParser PARSER = new AreaParser();
  private static final Pattern PREFIX = Pattern.compile("^([a-z]+)\\s*:\\s*(.+)?\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern MRGID = Pattern.compile("^https?://marineregions.org/mrgid/(\\d+)$", Pattern.CASE_INSENSITIVE);
  private static final String ISO = "iso";

  public AreaParser() {
    super(Area.class);
  }

  /**
   * @return the prefix/namespace of a CURIE value (incl. URIs) or null if it is not a CURIE
   */
  public static String parsePrefix(String x) {
    if (x != null) {
      var m = PREFIX.matcher(x);
      if (m.find()) {
        return m.group(1).toLowerCase();
      }
    }
    return null;
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

          } else if (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("urn")) {
            // deal with known domains, others will become free text
            m = MRGID.matcher(area);
            if (m.find()) {
              return Optional.of(new AreaImpl(Gazetteer.MRGID, m.group(1)));
            }

          } else {
            return Optional.ofNullable(AreaSerializer.parse(scheme + ":" + value.trim()));
          }
        }
        return Optional.of(new AreaImpl(area));

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
