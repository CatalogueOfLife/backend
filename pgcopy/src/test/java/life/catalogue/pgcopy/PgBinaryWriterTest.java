package life.catalogue.pgcopy;

import life.catalogue.common.io.TempFile;

import org.gbif.nameparser.api.Rank;

import java.io.FileOutputStream;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.postgresql.jdbc.PgConnection;

public class PgBinaryWriterTest {

  PgConnection con;

  @ClassRule
  public static PgRule pg = new PgRule();

  @Before
  public void init() throws SQLException {
    con = pg.connect();
    try (Statement st = con.createStatement()) {
      st.execute("DROP TABLE IF EXISTS test_name");
    }
  }

  @After
  public void teardown() throws SQLException {
    con.close();
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
      st.execute("DROP TYPE IF EXISTS RANK");
      st.execute("CREATE TYPE RANK AS ENUM ('KINGDOM','PHYLUM','CLASS','ORDER','FAMILY','GENUS','SPECIES')");
      st.execute("CREATE TABLE test_name (key int, name text, rank rank)");
    }
    return values;
  }

  @Test
  public void createNLoad() throws Exception {
    var values = prepareData();

    // dump and reload into new table
    try (TempFile tf = new TempFile();
         FileOutputStream out = new FileOutputStream(tf.file)
    ) {
      System.out.println(tf.file);
      try (PgBinaryWriter w = new PgBinaryWriter(out)) {
        int key = 1;
        for (var x : values) {
          w.startRow(3);
          w.writePInt(key++);
          w.writeString(x);
          w.writeEnum(Rank.SPECIES);
        }
      }
      PgCopyUtils.loadBinary(con, "test_name", List.of("key","name","rank"), tf.file);
    }
  }

}