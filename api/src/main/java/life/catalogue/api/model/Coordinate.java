package life.catalogue.api.model;

import java.util.Objects;

/**
 * WGS84 coordinate with decimal lat long.
 */
public class Coordinate {
  private double lat;
  private double lon;

  public Coordinate() {
  }

  public Coordinate(double longitude, double latitude) {
    this.lat = latitude;
    this.lon = longitude;
  }

  public double getLat() {
    return lat;
  }

  public void setLat(double lat) {
    this.lat = lat;
  }

  public double getLon() {
    return lon;
  }

  public void setLon(double lon) {
    this.lon = lon;
  }

  public boolean isValid(){
    if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
      return false;
    }
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Coordinate)) return false;
    Coordinate that = (Coordinate) o;
    return Double.compare(that.lat, lat) == 0 && Double.compare(that.lon, lon) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(lat, lon);
  }

  @Override
  public String toString() {
    return "φ" + lat + " λ" + lon;
  }

}