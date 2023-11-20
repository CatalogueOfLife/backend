package life.catalogue.postgres;

import life.catalogue.common.io.TabReader;
import life.catalogue.common.io.TempFile;
import life.catalogue.db.PgSetupRule;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
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
    PgCopyUtils.copyCSV(con, "person", "/test.csv", null, null);

    Map<String, Object> defs = ImmutableMap.of(
        "town", "Berlin"
    );
    Map<String, Function<String[], String>> funcs = ImmutableMap.of(
        "size", row -> String.valueOf(row[0].length()),
        "norm", row -> row[0].toLowerCase()
    );
    PgCopyUtils.copyCSV(con, "person", "/test.csv", defs, funcs);
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
      PgCopyUtils.dumpTSV(con, "SELECT name AS title, key AS id FROM p", tmp);
    } finally {
      tmp.delete();
    }
  }

  /**
   * Testing backslash escapes in roundtripping with tab reader/writer
   */
  @Test
  public void tsvRoundtrip() throws Exception {
    var values = List.of(
      "Rhypholophus-\n-simulans",
      "Shypholophus-\\\\n-simulans",
      "Limoniidae-Rhypholophus-\\n-simulans-28a397a9d",
      "Thypholophus-\\t-simulans\\r\\nxxx",
      "Vhypholophus (\n) simulans"
    );

    try (Statement st = con.createStatement()) {
      st.execute("CREATE TABLE test_name (key int, name text)");
      st.execute("CREATE TABLE test_name2 (key int, name text)");
    }

    // load weird data
    int idx = 1;
    try (Statement st = con.createStatement()) {
      for (var x : values) {
        String sql = String.format("INSERT INTO test_name (key, name) VALUES (%s, '%s')",idx,x);
        System.out.println(idx + " - " + x);
        System.out.println(sql);
        st.execute(sql);
        idx++;
      }
    }

    // dump as tab and reload into new table
    try (TempFile tf = new TempFile()) {
      System.out.println(tf.file);
      PgCopyUtils.dumpTSV(con, "SELECT key,name FROM test_name", tf.file);
      PgCopyUtils.copyTSV(con, "test_name2", tf.file);
      // verify TSV
      TabReader r = TabReader.tab(tf.file, StandardCharsets.UTF_8,0);
      idx = 0;
      for (String[] row : r) {
        if (idx==0) {
          idx++;
          continue; // header
        }
        System.out.println(row[0] + " - " + row[1]);
        assertEquals(String.valueOf(idx), row[0]);
        assertEquals(values.get(idx-1), row[1]);
        idx++;
      }
      assertEquals(6, idx);
    }

    try (Statement st = con.createStatement()) {
      st.execute("SELECT key,name FROM test_name");
      Map<Integer, String> names1 = new HashMap<>();
      try (ResultSet rs = st.getResultSet()) {
        while (rs.next()) {
          names1.put(rs.getInt(1), rs.getString(2));
        }
      }
      assertEquals(5, names1.size());

      st.execute("SELECT key,name FROM test_name2");
      Map<Integer, String> names2 = new HashMap<>();
      try (ResultSet rs = st.getResultSet()) {
        while (rs.next()) {
          names2.put(rs.getInt(1), rs.getString(2));
        }
      }
      assertEquals(5, names2.size());

      for (var ent : names1.entrySet()) {
        System.out.println(ent.getKey() + " - " + ent.getValue());
        assertEquals(values.get(ent.getKey()-1), ent.getValue());
      }

      assertEquals(names2, names1);
    }
  }

}