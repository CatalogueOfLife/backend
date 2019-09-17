package org.col.api.vocab;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import org.col.api.jackson.GeoTimeSerde;
import org.col.common.text.StringUtils;
import org.jetbrains.annotations.NotNull;

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
  
  private static final Comparator<GeoTime> DATE_ORDER = Comparator.comparing(GeoTime::getStart, Comparator.nullsLast(Comparator.naturalOrder()));
  private static final Comparator<GeoTime> NATURAL_ORDER = Comparator.comparing(GeoTime::getUnit).thenComparing(DATE_ORDER);
  
  public static final Map<String, GeoTime> TIMES = ImmutableMap.copyOf(GeoTimeFactory.readFile());
  
  /**
   * @return the matching geotime or null
   */
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
  
  GeoTime(GeoTime time, GeoTime parent) {
    this.name = time.name;
    this.unit = time.unit;
    this.source = time.source;
    this.colour = time.colour;
    this.start = time.start;
    this.end = time.end;
    this.parent = parent;
  }
  
  GeoTime(String name, GeoUnit unit, String source, Double start, Double end, String colour) {
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
