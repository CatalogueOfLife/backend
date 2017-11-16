package org.col.parser;

import com.google.common.base.CharMatcher;
import com.google.common.base.Enums;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.col.api.vocab.Country;
import org.col.api.vocab.Gazetteer;
import org.gbif.utils.file.csv.CSVReader;
import org.gbif.utils.file.csv.CSVReaderFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A parser that tries to extract complex area information (actual area id with gazatteer its based on )
 * from a simple location string.
 */
public class AreaParser implements Parser<AreaParser.Area> {
  public static final AreaParser PARSER = new AreaParser();
  private final Map<String, Gazetteer> gazetteerLookup;
  private final Pattern TDWG = Pattern.compile("^([1-9][0-9]?|[a-z]{3}(-[a-z]{2})?)$", Pattern.CASE_INSENSITIVE);
  private final Pattern FISHING = Pattern.compile("^[0-9]{1,2}(\\.([1-9]{1,2}|[a-z])){0,4}$", Pattern.CASE_INSENSITIVE);

  public AreaParser() {
    Map<String, Gazetteer> gaz = Maps.newHashMap();
    // load resources
    try {
      CSVReader reader = CSVReaderFactory.build(getClass().getResourceAsStream("/parser/dicts/gazateers.csv"), "UTF8", ",", null, 0);
      while (reader.hasNext()) {
        String[] row = reader.next();
        if (row.length == 2 && !Strings.isNullOrEmpty(row[1])) {
          com.google.common.base.Optional<Gazetteer> g = Enums.getIfPresent(Gazetteer.class, row[1]);
          if (g.isPresent()) {
            gaz.put(row[0].toUpperCase(), g.get());
          }
        }
      }
      reader.close();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load Area parser gazateer mappings", e);
    }

    // overlay enum
    for (Gazetteer g : Gazetteer.values()) {
      gaz.put(g.name().toUpperCase(), g);
    }
    this.gazetteerLookup = ImmutableMap.copyOf(gaz);
  }

  @Override
  public Optional<Area> parse(String area) throws UnparsableException {
    if (area == null || CharMatcher.invisible().and(CharMatcher.whitespace()).matchesAllOf(area)) {
      return Optional.empty();

    } else {
      // remove invisible
      String[] parts = CharMatcher.invisible().removeFrom(area).split(":", 2);
      if (parts.length > 1) {
        Gazetteer standard = parseStandard(parts[0]);
        return Optional.of(new Area(normalizeAndValidate(parts[1], standard), standard));
      } else {
        throw new UnparsableException("Invalid area code missing a gazetteer prefix");
      }
    }
  }

  /**
   * Removes all invisible chars and collapses and trims whitespace or removes it depending on the gazatteer
   */
  private String normalizeAndValidate(final String area, Gazetteer standard) throws UnparsableException {
    String areaClean;
    if (standard != Gazetteer.TEXT && standard != Gazetteer.LONGHURST && standard != Gazetteer.TEOW) {
      areaClean = CharMatcher.whitespace().trimAndCollapseFrom(area, ' ');
    } else {
      areaClean = CharMatcher.whitespace().removeFrom(area);
    }

    switch (standard) {
      case TDWG:
        if (!this.TDWG.matcher(areaClean).find()) {
          throw new UnparsableException("Unparsable TDWG area code: " + area);
        }
        return areaClean.toUpperCase();
      case ISO:
        Optional<Country> c = CountryParser.PARSER.parse(areaClean);
        return c.orElseThrow(() -> new UnparsableException(Country.class, area)).getIso2LetterCode();
      case FAO_FISHING:
        if (! this.FISHING.matcher(areaClean).find()) {
          throw new UnparsableException("Unparsable FAO fishing area: " + area);
        }
        return areaClean.toLowerCase();
      case IHO:
      case LONGHURST:
      case TEOW:
      case TEXT:
    }
    return areaClean;
  }

  private Gazetteer parseStandard(String standard) throws UnparsableException {
    if (gazetteerLookup.containsKey(CharMatcher.invisible().removeFrom(standard).toUpperCase())) {
      return gazetteerLookup.get(standard.toUpperCase());
    } else {
      throw new UnparsableException("Unparsable area standard: " + standard);
    }
  }

  public static class Area {
    public final String area;
    public final Gazetteer standard;

    public Area(String area, Gazetteer standard) {
      this.area = area;
      this.standard = standard;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Area area1 = (Area) o;
      return Objects.equals(area, area1.area) &&
          standard == area1.standard;
    }

    @Override
    public int hashCode() {
      return Objects.hash(area, standard);
    }
  }

}
