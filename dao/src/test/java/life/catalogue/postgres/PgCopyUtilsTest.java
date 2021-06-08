package life.catalogue.postgres;

import life.catalogue.db.PgSetupRule;

import java.io.File;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.function.Function;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.postgresql.jdbc.PgConnection;

import com.google.common.collect.ImmutableMap;

import static org.junit.Assert.assertEquals;

public class PgCopyUtilsTest {
  
  PgConnection con;
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

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
  public void buildNsplit() throws Exception {
    assertEquals("{Duméril,Bibron}", PgCopyUtils.buildPgArray( PgCopyUtils.splitPgArray("{Duméril,Bibron}")) );
  }
  
  @Test
  public void copy() throws Exception {
    try (Statement st = con.createStatement()) {
      st.execute("CREATE TABLE person (key serial primary key, name text, age int, town text, norm text, size int)");
    }
    PgCopyUtils.copy(con, "person", "/test.csv", null, null);

    Map<String, Object> defs = ImmutableMap.of(
        "town", "Berlin"
    );
    Map<String, Function<String[], String>> funcs = ImmutableMap.of(
        "size", row -> String.valueOf(row[0].length()),
        "norm", row -> row[0].toLowerCase()
    );
    PgCopyUtils.copy(con, "person", "/test.csv", defs, funcs);
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