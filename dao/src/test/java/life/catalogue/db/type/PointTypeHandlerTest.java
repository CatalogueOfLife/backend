package life.catalogue.db.type;

import life.catalogue.api.model.Coordinate;

import org.junit.Test;

import java.sql.SQLException;

import static org.junit.Assert.*;

public class PointTypeHandlerTest {

  @Test
  public void toCoord() throws SQLException {
    assertNull(PointTypeHandler.toCoord(null));
    assertNull(PointTypeHandler.toCoord(""));
    assertNull(PointTypeHandler.toCoord("null"));

    assertEquals(new Coordinate(1,2), PointTypeHandler.toCoord("(1,2)"));
    assertEquals(new Coordinate(1.234,-21.45621), PointTypeHandler.toCoord("(1.234,-21.45621)"));
  }
}