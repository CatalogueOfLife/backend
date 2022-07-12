package life.catalogue.parser;

import life.catalogue.api.model.Coordinate;

import java.util.Optional;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CoordParserTest {

  @Test
  public void parse() throws Exception {
    CoordParser cp = CoordParser.PARSER;
    Optional<Coordinate> ll = cp.parse("24.134", "-13.02");
    assertEquals(new Coordinate(24.134, -13.02), ll.get());
  }

  @Test(expected = UnparsableException.class)
  public void parseOutOfBounds() throws Exception {
    CoordParser cp = CoordParser.PARSER;
    Optional<Coordinate> ll = cp.parse("24.134", "-113.02");
  }
}