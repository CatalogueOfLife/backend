package org.col.parser;

import com.google.common.base.CharMatcher;
import org.col.api.vocab.Gazetteer;

import java.util.Objects;
import java.util.Optional;

/**
 * A parser that tries to extract complex area information (actual area id with gazatteer its based on )
 * from a simple location string.
 */
public class AreaParser implements Parser<AreaParser.Area> {
  public static final AreaParser PARSER = new AreaParser();

  @Override
  public Optional<Area> parse(String area) throws UnparsableException {
    if (area == null || CharMatcher.invisible().matchesAllOf(area)) {
      return Optional.empty();

    } else {
      String[] parts = area.split(":", 2);
      if (parts.length > 1) {
        Gazetteer standard = parseStandard(parts[0]);
        return Optional.of(new Area(normalizeAndValidate(parts[1], standard), standard));
      } else {
        throw new UnparsableException("Invalid area code missing a gazetteer prefix");
      }
    }
  }

  private static String normalizeAndValidate(String area, Gazetteer standard) throws UnparsableException {
    switch (standard) {
      case TDWG:
      case ISO:
        //TODO: parse country
        return area.toUpperCase().trim();
    }
    return area.trim();
  }

  private Gazetteer parseStandard(String standard) throws UnparsableException {
    try {
      return Gazetteer.valueOf(standard.toUpperCase());
    } catch (IllegalArgumentException e) {
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
