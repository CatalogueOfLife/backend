package life.catalogue.parser;

import life.catalogue.api.model.Coordinate;

import java.util.Optional;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CoordParserTest {

  @Test
  public void parse() throws Exception {
    assertCoord("24.134", "-13.02", 24.134, -13.02);
    assertCoord("13.26", "93.26", 13.26, 93.26);
    assertCoord("+39.69", "-105.64", 39.69, -105.64);
  }

  void assertCoord(String lat, String lon, double latExpected, double lonExpected) throws UnparsableException {
    Optional<Coordinate> ll = CoordParser.PARSER.parse(lon, lat);
    assertEquals(new Coordinate(lonExpected, latExpected), ll.get());
  }

  @Test(expected = UnparsableException.class)
  public void parseOutOfBounds() throws Exception {
    CoordParser cp = CoordParser.PARSER;
    Optional<Coordinate> ll = cp.parse("24.134", "-113.02");
  }
}