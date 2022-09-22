package life.catalogue.api.vocab;

import life.catalogue.api.jackson.GeoTimeSerde;
import life.catalogue.common.io.Resources;
import life.catalogue.common.text.CSVUtils;
import life.catalogue.common.text.StringUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

/**
 * A geochronological time span with a given scale.
 * Temporal position expressed numerically scaled in millions of years increasing backwards relative to 1950.
 * Positions in geologic time are conventionally denoted in millions of years, measured backwards from the present,
 * which is fixed to 1950 when the precision requires it.

 * For more see:
 *
 * https://www.seegrid.csiro.au/wiki/CGIModel/GeologicTime
 * http://www.stratigraphy.org/index.php/ics-chart-timescale
 * https://en.wikipedia.org/wiki/List_of_geochronologic_names
 *
 * Natural order of GeoTime is by its scale with (super) eons first, then chronologically by the starting time.
 */
public class GeoTime implements Comparable<GeoTime> {
  
  private static final Comparator<GeoTime> DATE_ORDER = Comparator.comparing(GeoTime::getStart, Comparator.nullsLast(Comparator.naturalOrder()));
  private static final Comparator<GeoTime> NATURAL_ORDER = Comparator.comparing(GeoTime::getType).thenComparing(DATE_ORDER);
  
  public static String ISC_RESOURCE = "vocab/geotime/geotime.csv";
  public static final Map<String, GeoTime> TIMES = ImmutableMap.copyOf(readCSV());

  private static Map<String, GeoTime> readCSV() {
    Map<String, GeoTime> map = new HashMap<>();
    boolean header = true;
    for (var row : CSVUtils.parse(Resources.stream(ISC_RESOURCE)).collect(Collectors.toList())) {
      if (header) {
        header = false;
        continue;
      }
      GeoTime gt = new GeoTime(row.get(0), GeoTimeType.valueOf(row.get(1)), dbl(row.get(2)), dbl(row.get(3)), map.get(norm(row.get(4))));
      map.put(norm(gt.getName()), gt);
    }
    return map;
  }
  private static Double dbl(String x) {
    return x == null ? null : Double.parseDouble(x);
  }

  /**
   * @return the matching geotime or null
   */
  public static GeoTime byName(String name) {
    if (name == null) return null;
    return TIMES.getOrDefault(norm(name), null);
  }
  
  private static String norm(String x) {
    return x == null ? null : StringUtils.foldToAscii(x).trim().toUpperCase();
  }
  
  /**
   * The official ICS time span name in english.
   */
  private final String name;
  
  /**
   * Rank/scale/unit of the timespan
   */
  private final GeoTimeType type;

  /**
   * In million years (Ma)
   */
  private final Double start;
  
  /**
   * In million years (Ma)
   */
  private final Double end;
  
  /**
   * Time span this time is a subdivision of.
   */
  @JsonIgnore
  private final GeoTime parent;

  GeoTime(GeoTime gt, GeoTime parent) {
    this(gt.getName(), gt.getType(), gt.getStart(), gt.getEnd(), parent);
  }

  GeoTime(String name, GeoTimeType type, Double start, Double end, GeoTime parent) {
    this.name = Preconditions.checkNotNull(name, "missing name for geotime");
    this.type = Preconditions.checkNotNull(type, "missing type for " + name);
    this.start = start;
    this.end = end;
    this.parent = parent;
  }
  
  public String getName() {
    return name;
  }
  
  public GeoTimeType getType() {
    return type;
  }

  public Double getStart() {
    return start;
  }
  
  public Double getEnd() {
    return end;
  }

  /**
   * @param ma million years before today. Positive number required!
   * @return true if the given point in time is between the start and end time (inclusive) of the instance
   */
  public boolean includes(double ma) {
    if (ma < 0) throw new IllegalArgumentException("million years given must be positive");
    if (start != null) {
      if (end != null) {
        return start >= ma
               && ma >= end;
      } else {
        // open ended
        return start >= ma;
      }
    } else if (end != null) {
      // open start
      return ma >= end;
    }
    return false;
  }

  /**
   * @return duration in million years or null if open ended
   */
  public Double duration() {
    if (start != null && end != null) {
      return start-end;
    }
    return null;
  }

  @JsonSerialize(using = GeoTimeSerde.Serializer.class)
  public GeoTime getParent() {
    return parent;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GeoTime geoTime = (GeoTime) o;
    return Objects.equals(name, geoTime.name) &&
        type == geoTime.type &&
        Objects.equals(start, geoTime.start) &&
        Objects.equals(end, geoTime.end) &&
        Objects.equals(parent, geoTime.parent);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(name, type, start, end, parent);
  }
  
  @Override
  public int compareTo(@NotNull GeoTime o) {
    return NATURAL_ORDER.compare(this, o);
  }
  
  @Override
  public String toString() {
    return String.format("%s %s: %s-%s", name, type, start, end);
  }

}
