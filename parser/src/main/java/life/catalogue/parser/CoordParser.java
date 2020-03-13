package life.catalogue.parser;

import org.apache.commons.lang3.StringUtils;

import java.util.Objects;
import java.util.Optional;

/**
 * Parses doubles throwing UnparsableException in case the value is not empty but unparsable.
 */
public class CoordParser {
  public static final CoordParser PARSER = new CoordParser();

  public static class LatLon {
    public final double lat;
    public final double lon;

    public LatLon(double lat, double lon) {
      this.lat = lat;
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
      if (o == null || getClass() != o.getClass()) return false;
      LatLon latLon = (LatLon) o;
      return Double.compare(latLon.lat, lat) == 0 &&
          Double.compare(latLon.lon, lon) == 0;
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

  public Optional<LatLon> parse(String latitude, String longitude) throws UnparsableException {
    if (StringUtils.isBlank(latitude) && StringUtils.isBlank(longitude)) {
      return Optional.empty();
    }

    Optional<Double> lat = DecimalParser.PARSER.parse(latitude);
    Optional<Double> lon = DecimalParser.PARSER.parse(longitude);
    if (lat.isPresent() && lon.isPresent()) {
      LatLon ll = new LatLon(lat.get(), lon.get());
      if (ll.isValid()) {
        return Optional.of(ll);
      }
      throw new UnparsableException(LatLon.class, ll.toString(), "Out of bounds");
    }
    throw new UnparsableException(LatLon.class, "lat="+latitude + "; lon=" + longitude);
  }
}
