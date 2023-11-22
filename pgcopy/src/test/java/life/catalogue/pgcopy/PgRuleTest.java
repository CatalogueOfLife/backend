package life.catalogue.pgcopy;

import org.junit.Rule;
import org.junit.Test;

import java.sql.SQLException;

import static org.junit.Assert.*;

public class PgRuleTest {

  @Rule
  public PgRule pg = new PgRule();

  @Test
  public void connect() throws SQLException {
    try (var c = pg.connect()) {
      System.out.println(c);
      System.out.println(c.getBackendPID());
    }
  }
}