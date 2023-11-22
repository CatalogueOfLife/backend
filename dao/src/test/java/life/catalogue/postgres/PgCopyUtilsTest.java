package life.catalogue.postgres;

import de.bytefish.pgbulkinsert.row.SimpleRowWriter;

import life.catalogue.common.io.TabReader;
import life.catalogue.common.io.TempFile;
import life.catalogue.db.PgConnectionRule;
import life.catalogue.db.PgSetupRule;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import life.catalogue.db.SqlSessionFactoryRule;

import org.gbif.nameparser.api.Rank;

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
  public static PgSetupRule pgRule = new PgSetupRule();
  //public static SqlSessionFactoryRule pgRule = new PgConnectionRule("clb", "postgres", "postgres");

  @Before
  public void init() throws SQLException {
    con = pgRule.connect();
    try (Statement st = con.createStatement()) {
      for (String tbl : List.of("person", "test_name", "test_name2")) {
        st.execute("DROP TABLE IF EXISTS " + tbl);
      }
    }
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
    var values = prepareData();

    // dump as tab and reload into new table
    try (TempFile tf = new TempFile()) {
      System.out.println(tf.file);
      PgCopyUtils.dumpTSV(con, "SELECT key,name,rank FROM test_name", tf.file);
      PgCopyUtils.copyTSV(con, "test_name2", tf.file);

      // verify TSV
      TabReader r = TabReader.tab(tf.file, StandardCharsets.UTF_8,0);
      int idx = 0;
      for (String[] row : r) {
        if (idx==0) {
          idx++;
          continue; // header
        }
        System.out.println(row[0] + " - " + row[1]);
        assertEquals(String.valueOf(idx), row[0]);
        assertEquals(values.get(idx-1), row[1]);
        assertEquals(Rank.SPECIES.name(), row[2]);
        idx++;
      }
      assertEquals(6, idx);
    }

    // checks db content
    verifyData(values.size());
  }


  private List<String> prepareData() throws SQLException {
    var values = List.of(
      "Rhypholophus-\n-simulans",
      "Shypholophus-\\\\n-simulans",
      "Limoniidae-Rhypholophus-\\n-simulans-28a397a9d",
      "Thypholophus-\\t-simulans\\r\\nxxx",
      "Vhypholophus (\n) simulans"
    );

    try (Statement st = con.createStatement()) {
      st.execute("CREATE TABLE test_name (key int, name text, rank rank)");
      st.execute("CREATE TABLE test_name2 (key int, name text, rank rank)");
    }

    // load weird data
    int idx = 1;
    try (Statement st = con.createStatement()) {
      for (var x : values) {
        String sql = String.format("INSERT INTO test_name (key, name, rank) VALUES (%s, '%s', '%s'::rank)", idx,x, Rank.SPECIES);
        System.out.println(idx + " - " + x);
        System.out.println(sql);
        st.execute(sql);
        idx++;
      }
    }
    return values;
  }

  private int verifyData(int total) throws SQLException {
    int cnt;
    try (Statement st = con.createStatement()) {
      st.execute("SELECT count(*) FROM test_name");
      try (ResultSet rs = st.getResultSet()) {
        rs.next();
        cnt = rs.getInt(1);
        assertEquals(total, cnt);
      }
      st.execute("SELECT count(*) FROM test_name2");
      try (ResultSet rs = st.getResultSet()) {
        rs.next();
        int cnt2 = rs.getInt(1);
        assertEquals(cnt2, cnt);
      }
      st.execute("SELECT count(*) FROM test_name n1 JOIN test_name2 n2 ON n1.key=n2.key AND n1.name=n2.name AND n1.rank=n2.rank");
      try (ResultSet rs = st.getResultSet()) {
        rs.next();
        int cnt2 = rs.getInt(1);
        assertEquals("Not the same records", cnt2, cnt);
      }
    }
    return cnt;
  }

  /**
   * Use binary pg format for copy
   */
  @Test
  public void binaryRoundtrip() throws Exception {
    var values = prepareData();

    // dump and reload into new table
    try (TempFile tf = new TempFile()) {
      System.out.println(tf.file);
      PgCopyUtils.dumpBinary(con, "SELECT key,name,rank FROM test_name", tf.file);
      PgCopyUtils.copyBinary(con, "test_name2", List.of("key","name","rank"), tf.file);
    }

    verifyData(values.size());
  }

}