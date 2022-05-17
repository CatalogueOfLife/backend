package life.catalogue.parser;

import java.util.Objects;
import java.util.Optional;

import life.catalogue.api.model.Coordinate;

import org.apache.commons.lang3.StringUtils;

/**
 * Parses doubles throwing UnparsableException in case the value is not empty but unparsable.
 */
public class CoordParser {
  public static final CoordParser PARSER = new CoordParser();

  public Optional<Coordinate> parse(String longitude, String latitude) throws UnparsableException {
    if (StringUtils.isBlank(latitude) && StringUtils.isBlank(longitude)) {
      return Optional.empty();
    }

    var lat = DecimalParser.PARSER.parse(latitude);
    var lon = DecimalParser.PARSER.parse(longitude);
    if (lat.isPresent() && lon.isPresent()) {
      Coordinate ll = new Coordinate(lon.get(), lat.get());
      if (ll.isValid()) {
        return Optional.of(ll);
      }
      throw new UnparsableException(Coordinate.class, ll.toString(), "Out of bounds");
    }
    throw new UnparsableException(Coordinate.class, "lat="+latitude + "; lon=" + longitude);
  }
}
