package org.col.postgres;

import java.io.File;
import java.sql.SQLException;
import java.sql.Statement;

import org.col.db.PgSetupRule;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.postgresql.jdbc.PgConnection;

public class PgCopyUtilsTest {
  
  PgConnection con;
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule(true, false);

  @Before
  public void init() throws SQLException {
    con = pgSetupRule.connect();
    PgSetupRule.wipeDB(con);
  }
  
  @After
  public void teardown() throws SQLException {
    con.close();
  }

  @Test
  public void dump() throws Exception {
    try (Statement st = con.createStatement()) {
      st.execute("CREATE TABLE p (key serial primary key, name text)");
      st.execute("INSERT INTO p (name) select md5(random()::text) from generate_series(1, 1000)");
    }
  
    File tmp = File.createTempFile("colplus", "csv");
    System.out.println(tmp.getAbsolutePath());
    try {
      PgCopyUtils.dump(con, "SELECT name AS title, key AS id FROM p", tmp);
    } finally {
      tmp.delete();
    }
  }
}