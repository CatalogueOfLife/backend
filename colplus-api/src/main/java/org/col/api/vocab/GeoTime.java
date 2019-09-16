package org.col.api.vocab;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import org.col.api.util.JsonLdReader;
import org.col.common.io.Resources;
import org.col.common.text.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A geochronological time span.
 * Temporal position expressed numerically scaled in millions of years increasing backwards relative to 1950.
 * Positions in geologic time are conventionally denoted in millions of years, measured backwards from the present,
 * which is fixed to 1950 when the precision requires it.

 * For more see:
 *
 * https://www.seegrid.csiro.au/wiki/CGIModel/GeologicTime
 * http://www.stratigraphy.org/index.php/ics-chart-timescale
 * https://en.wikipedia.org/wiki/List_of_geochronologic_names
 *
 * Natural order of GeoTime is by its unit with (super) eons first, then chronologically by the starting time.
 */
public class GeoTime implements Comparable<GeoTime> {
  
  private static final Logger LOG = LoggerFactory.getLogger(GeoTime.class);
  private static final Comparator<GeoTime> DATE_ORDER = Comparator.comparing(GeoTime::getStart, Comparator.nullsLast(Comparator.naturalOrder()));
  private static final Comparator<GeoTime> NATURAL_ORDER = Comparator.comparing(GeoTime::getUnit).thenComparing(DATE_ORDER);
  public static String ISC_FILE = "vocab/geotime/GeoSciML-isc2017.json";
  private static Pattern BOUNDS = Pattern.compile("(younger|older) bound -[\\d.]+ Ma");
  private static Pattern PREFIX = Pattern.compile("^[a-z]{1,4}:");
  
  public static final Map<String, GeoTime> TIMES = ImmutableMap.copyOf(load());
  
  private static Map<String, GeoTime> load() {
    try {
      InputStream stream = Resources.stream(ISC_FILE);
      JsonLdReader.JsonLD json = JsonLdReader.read(stream);
      Map<String, String> hasParent = new HashMap<>();
      List<GeoTime> times = new ArrayList<>();
      for (JsonLdReader.LDItem item : json.graph) {
        if (!item.type.contains("gts:GeochronologicEra")
            || item.isReplacedBy != null
        ) {
          //System.out.println(item.type);
          //if (item.type.contains("time:Duration") || item.type.contains("time:TimePosition")) {
          //  System.out.println(item);
          //}
          //if (item.type.contains("gts:GeochronologicBoundary")) {
          //  System.out.println(item);
          //}

          // skip all other entry type
          continue;
        }
        final String name = findEnLabel(item.prefLabel);
        if (item.broader != null) {
          hasParent.put(name, removePrefix(item.broader));
        }
        GeoTime gt = new GeoTime(
            name,
            findUnit(item.type),
            removePrefix(item.inScheme),
            null,
            null,
            null
        );
        times.add(gt);
      }
  
      Map<String, GeoTime> map = new HashMap<>();
      // sorts by unit, then time
      Collections.sort(times);
      for (GeoTime src : times) {
        GeoTime parent = null;
        if (hasParent.containsKey(src.getName())) {
          parent = map.get(norm(hasParent.get(src.getName())));
        }
        map.put(norm(src.getName()), new GeoTime(src, parent));
      }
      return map;
      
    } catch (IOException e) {
      LOG.error("Failed to read geotime JsonLD", e);
      throw new RuntimeException(e);
    }
  }
  
  private static String removePrefix(String value) {
    return PREFIX.matcher(value).replaceFirst("");
  }
  
  private static GeoUnit findUnit(List<String> types) {
    for (String t : types) {
      t = removePrefix(t).toUpperCase().replaceAll("-", "");
      try {
        return GeoUnit.valueOf(t);
      } catch (IllegalArgumentException e) {
        // ignore, many other types included in this list
      }
    }
    LOG.debug("No geotime unit found in types {}", types);
    return null;
  }
  
  private static String findEnLabel(List<JsonLdReader.Label> labels) {
    if (labels != null) {
      for (JsonLdReader.Label l : labels) {
        if (l.language.equalsIgnoreCase("en")) {
          return l.value;
        }
      }
    }
    LOG.debug("No english label found for labels {}", labels);
    return null;
  }

  public static GeoTime byName(String name) {
    if (name == null) return null;
    return TIMES.getOrDefault(norm(name), null);
  }
  
  private static String norm(String x) {
    return StringUtils.foldToAscii(x).trim().toUpperCase();
  }
  
  /**
   * The official ICS time span name in english.
   */
  private final String name;
  
  /**
   * Rank/unit of the timespan
   */
  private final GeoUnit unit;
  
  /**
   * The source the definition is coming from
   */
  private final String source;

  /**
   * In million years (Ma)
   */
  private final Double start;
  
  /**
   * In million years (Ma)
   */
  private final Double end;
  
  /**
   * RGB hex color
   */
  private final String colour;

  /**
   * Time span this time is a subdivision of.
   */
  @JsonIgnore
  private final GeoTime parent;
  
  private GeoTime(GeoTime time, GeoTime parent) {
    this.name = time.name;
    this.unit = time.unit;
    this.source = time.source;
    this.colour = time.colour;
    this.start = time.start;
    this.end = time.end;
    this.parent = parent;
  }
  
  private GeoTime(String name, GeoUnit unit, String source, Double start, Double end, String colour) {
    this.name = Preconditions.checkNotNull(name, "missing name for geotime");
    this.unit = Preconditions.checkNotNull(unit, "missing unit for "+name);
    this.source = source;
    this.colour = colour;
    this.start = start;
    this.end = end;
    this.parent = null;
  }
  
  public String getName() {
    return name;
  }
  
  public GeoUnit getUnit() {
    return unit;
  }
  
  public String getSource() {
    return source;
  }
  
  public Double getStart() {
    return start;
  }
  
  public Double getEnd() {
    return end;
  }
  
  public String getColour() {
    return colour;
  }
  
  public GeoTime getParent() {
    return parent;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GeoTime geoTime = (GeoTime) o;
    return Objects.equals(name, geoTime.name) &&
        unit == geoTime.unit &&
        Objects.equals(source, geoTime.source) &&
        Objects.equals(start, geoTime.start) &&
        Objects.equals(end, geoTime.end) &&
        Objects.equals(colour, geoTime.colour) &&
        Objects.equals(parent, geoTime.parent);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(name, unit, source, start, end, colour, parent);
  }
  
  @Override
  public int compareTo(@NotNull GeoTime o) {
    return NATURAL_ORDER.compare(this, o);
  }
  
  @Override
  public String toString() {
    return name + " " + unit;
  }
}
