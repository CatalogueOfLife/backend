package life.catalogue.db.type;

import life.catalogue.db.type2.NamePartSetTypeHandler;

import org.gbif.nameparser.api.NamePart;

import java.sql.Array;
import java.sql.SQLException;
import java.util.EnumSet;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BaseEnumSetTypeHandlerTest {

  final NamePartSetTypeHandler handler = new NamePartSetTypeHandler();

  static Array pgArray(Object values) throws SQLException {
    Array a = mock(Array.class);
    when(a.getArray()).thenReturn(values);
    return a;
  }

  @Test
  public void flat() throws SQLException {
    assertEquals(EnumSet.of(NamePart.SPECIFIC), handler.toObj(pgArray(new String[]{"SPECIFIC"})));
    assertEquals(EnumSet.of(NamePart.GENERIC, NamePart.INFRASPECIFIC),
      handler.toObj(pgArray(new String[]{"GENERIC", "INFRASPECIFIC"})));
  }

  @Test
  public void empty() throws SQLException {
    assertEquals(EnumSet.noneOf(NamePart.class), handler.toObj(pgArray(new String[0])));
    assertEquals(EnumSet.noneOf(NamePart.class), handler.toObj(pgArray(null)));
    assertEquals(EnumSet.noneOf(NamePart.class), handler.toObj(null));
  }

  /**
   * Postgres does not enforce dimensionality on array columns, so a bad migration can leave
   * multidimensional values behind. Those must fail loudly instead of being turned into a
   * bogus enum name, see https://github.com/CatalogueOfLife/backend the notho v5 migration.
   */
  @Test
  public void multidimensional() throws SQLException {
    try {
      handler.toObj(pgArray(new String[][]{{"SPECIFIC"}}));
      fail("multidimensional array must be rejected");
    } catch (SQLException e) {
      assertTrue(e.getMessage(), e.getMessage().contains("multidimensional"));
      assertTrue(e.getMessage(), e.getMessage().contains("NAMEPART"));
    }
  }
}
