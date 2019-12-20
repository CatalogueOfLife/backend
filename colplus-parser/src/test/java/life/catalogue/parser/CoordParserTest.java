package life.catalogue.parser;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

public class CoordParserTest {

  @Test
  public void parse() throws Exception {
    CoordParser cp = CoordParser.PARSER;
    Optional<CoordParser.LatLon> ll = cp.parse("-13.02", "24.134");
    assertEquals(new CoordParser.LatLon(-13.02, 24.134), ll.get());
  }

  @Test(expected = UnparsableException.class)
  public void parseOutOfBounds() throws Exception {
    CoordParser cp = CoordParser.PARSER;
    Optional<CoordParser.LatLon> ll = cp.parse("-113.02", "24.134");
  }
}