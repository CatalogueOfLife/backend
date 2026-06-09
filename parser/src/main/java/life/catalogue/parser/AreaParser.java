package life.catalogue.parser;

import life.catalogue.api.vocab.area.Area;
import life.catalogue.api.vocab.area.GenericArea;
import life.catalogue.api.vocab.area.Gazetteer;
import life.catalogue.common.kryo.AreaSerializer;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.CharMatcher;

import javax.annotation.Nullable;

/**
 * A parser that tries to extract complex area information (actual area id with gazetteer its based on )
 * from a simple location string.
 */
public class AreaParser extends ParserBase<Area> {
  public static final AreaParser PARSER = new AreaParser();
  private static final Pattern PREFIX = Pattern.compile("^([a-z_0-9-]+)\\s*:\\s*(.+)?\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern MRGID_URL = Pattern.compile("^https?://marineregions.org/mrgid/(\\d+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern ISO_3166_2 = Pattern.compile("^([a-z]{2})-([a-z0-9]{1,3})$", Pattern.CASE_INSENSITIVE);
  private static final Set<String> ISO_PREFIXES = Set.of("iso", "3166", "iso3166", "iso31662", "country");
  private @Nullable AreaLabelLookup labelLookup;

  public AreaParser() {
    super(Area.class);
  }

  public void setLabelLookup(@Nullable AreaLabelLookup labelLookup) {
    this.labelLookup = labelLookup;
  }

  private static String normaliseGazetteer(String gaz) {
    var scheme = gaz.replaceAll("[_-]", "").toLowerCase();
    if (ISO_PREFIXES.contains(scheme)) {
      return Gazetteer.ISO.name().toLowerCase();

    } else if (scheme.equalsIgnoreCase("realm") || scheme.equalsIgnoreCase("bio")) {
      return Gazetteer.REALM.name().toLowerCase();
    }
    return scheme;
  }

  @Override
  public Optional<? extends Area> parse(String area) throws UnparsableException {
    if (area == null || CharMatcher.invisible().and(CharMatcher.whitespace()).matchesAllOf(area)) {
      return Optional.empty();
    }

    // remove invisible
    area = area.trim();
    var m = PREFIX.matcher(area);
    if (m.find()) {
      String scheme = normaliseGazetteer(m.group(1));
      String value = m.group(2);
      return parse(scheme, value);
    }
    return Optional.of(new GenericArea(area));
  }

  public Optional<? extends Area> parse(String scheme ,String value) throws UnparsableException {
    if (StringUtils.isBlank(value)) {
      return Optional.empty();

    } else if (StringUtils.isBlank(scheme)) {
      // no gazetteer = TEXT
      return Optional.of(new GenericArea(value));

    } else if (ISO_PREFIXES.contains(scheme)) {
      if (value.length() > 3 && value.contains("-")) {
        return parseIsoSubRegions(value);
      }
      return CountryParser.PARSER.parse(value.trim());

    } else if (scheme.equalsIgnoreCase(Gazetteer.REALM.name())) {
      return RealmParser.PARSER.parse(value.trim());

    } else if (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("urn")) {
      // deal with known domains, others will become free text
      var m = MRGID_URL.matcher(scheme + ":" + value);
      if (m.find()) {
        var label = labelLookup == null ? null : labelLookup.findLabel(Gazetteer.MRGID, m.group(1));
        return Optional.of(new GenericArea(Gazetteer.MRGID, m.group(1), label));
      }

    } else if (scheme.equalsIgnoreCase(Gazetteer.MRGID.name())) {
      // must be integers
      try {
        Integer.parseInt(value);
        var label = labelLookup == null ? null : labelLookup.findLabel(Gazetteer.MRGID, value);
        return Optional.of(new GenericArea(Gazetteer.MRGID, value, label));
      } catch (NumberFormatException e) {
        throw new UnparsableException("Invalid area code " + value + " for MRGID gazetteer");
      }

    } else {
      var gaz = SafeParser.parse(GazetteerParser.PARSER, scheme).orNull();
      if (gaz != null) {
        value = gaz.normalize(value.trim());
        var m2 = gaz.getRegex().matcher(value);
        if (m2.matches()) {
          var label = labelLookup == null ? null : labelLookup.findLabel(gaz, value);
          return Optional.of(new GenericArea(gaz, value, label));
        }
      }
    }
    throw new UnparsableException("Invalid area code " + value + " for gazetteer "+scheme);
  }

  /**
   * Parses ISO 3166-2 https://de.wikipedia.org/wiki/ISO_3166-2
   */
  private Optional<GenericArea> parseIsoSubRegions(String x) {
    var areaID = StringUtils.deleteWhitespace(x).toUpperCase();
    var m = ISO_3166_2.matcher(areaID);
    if (m.find()) {
      var label = labelLookup == null ? null : labelLookup.findLabel(Gazetteer.ISO, areaID);
      return Optional.of(new GenericArea(Gazetteer.ISO, areaID, label));
    }
    return Optional.empty();
  }

  @Override
  Area parseKnownValues(String upperCaseValue) throws UnparsableException {
    throw new UnsupportedOperationException();
  }

}
