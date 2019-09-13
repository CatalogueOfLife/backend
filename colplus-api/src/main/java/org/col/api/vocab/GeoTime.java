package org.col.api.vocab;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

/**
 * A geochronological time span
 * See https://en.wikipedia.org/wiki/List_of_geochronologic_names
 *
 * http://www.stratigraphy.org/index.php/ics-chart-timescale
 * http://vocabs.ands.org.au/repository/api/lda/csiro/international-chronostratigraphic-chart/2018-revised-corrected/resource?uri=http://resource.geosciml.org/classifier/ics/ischart/GeochronologicEras
 *
 * Natural order of GeoTime is by its unit with (super) eons first, then chronologically by the starting time.
 */
public class GeoTime implements Comparable<GeoTime> {
  
  private static final Comparator<GeoTime> NATURAL_ORDER = Comparator.comparing(GeoTime::getUnit)
          .thenComparing( Comparator.nullsLast(Comparator.comparing(GeoTime::getStart).reversed()) );
  
  /**
   * The official ICS time span name.
   */
  private final String name;
  
  /**
   * Rank/unit of the timespan
   */
  private final GeoUnit unit;
  
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
  
  @JsonIgnore
  private final List<GeoTime> subdivisions;
  
  /**
   * Creates an immutable instance with an immutable subdivision list
   */
  public GeoTime(String name, GeoUnit unit, Double start, Double end, GeoTime parent, List<GeoTime> subdivisions) {
    this.name = name;
    this.unit = unit;
    this.start = start;
    this.end = end;
    this.parent = parent;
    this.subdivisions = ImmutableList.copyOf(subdivisions);
  }
  
  /**
   * Creates an immutable instance with an mutable, empty subdivision list
   */
  public GeoTime(String name, GeoUnit unit, Double start, Double end, GeoTime parent) {
    this.name = name;
    this.unit = unit;
    this.start = start;
    this.end = end;
    this.parent = parent;
    this.subdivisions = new ArrayList<>();
  }
  
  public String getName() {
    return name;
  }
  
  public GeoUnit getUnit() {
    return unit;
  }
  
  public Double getStart() {
    return start;
  }
  
  public Double getEnd() {
    return end;
  }
  
  public GeoTime getParent() {
    return parent;
  }
  
  public List<GeoTime> getSubdivisions() {
    return subdivisions;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GeoTime geoTime = (GeoTime) o;
    return Objects.equals(name, geoTime.name) &&
        unit == geoTime.unit &&
        Objects.equals(start, geoTime.start) &&
        Objects.equals(end, geoTime.end) &&
        Objects.equals(parent, geoTime.parent) &&
        Objects.equals(subdivisions, geoTime.subdivisions);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(name, unit, start, end, parent, subdivisions);
  }
  
  @Override
  public int compareTo(@NotNull GeoTime o) {
    return NATURAL_ORDER.compare(this, o);
  }
}
