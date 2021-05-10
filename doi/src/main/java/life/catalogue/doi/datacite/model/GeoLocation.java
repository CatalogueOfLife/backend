package life.catalogue.doi.datacite.model;

import java.util.Objects;

public class GeoLocation {
  private String geoLocationPlace;

  public String getGeoLocationPlace() {
    return geoLocationPlace;
  }

  public void setGeoLocationPlace(String geoLocationPlace) {
    this.geoLocationPlace = geoLocationPlace;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GeoLocation)) return false;
    GeoLocation that = (GeoLocation) o;
    return Objects.equals(geoLocationPlace, that.geoLocationPlace);
  }

  @Override
  public int hashCode() {
    return Objects.hash(geoLocationPlace);
  }
}
