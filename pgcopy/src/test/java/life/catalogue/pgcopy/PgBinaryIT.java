package life.catalogue.pgcopy;

import life.catalogue.common.io.TempFile;

import org.gbif.nameparser.api.Rank;

import org.junit.*;
import org.postgresql.jdbc.PgConnection;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import static life.catalogue.pgcopy.PgBinaryReaderTest.GENDER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PgBinaryIT {

  PgConnection con;

  @ClassRule
  public static PgRule pg = new PgRule();

  @Before
  public void init() throws SQLException {
    con = pg.connect();
    try (Statement st = con.createStatement()) {
      st.execute("DROP TABLE IF EXISTS person");
      st.execute("DROP TYPE IF EXISTS GENDER");
    }
  }

  @After
  public void teardown() throws SQLException {
    con.close();
  }

  class Person {
    int key;
    GENDER gender;
    String surname;
    List<String> firstnames;

    public Person(int key, GENDER gender, String surname, List<String> firstnames) {
      this.key = key;
      this.gender = gender;
      this.surname = surname;
      this.firstnames = firstnames;
    }
  }

  private List<Person> prepareData() throws SQLException {
    var values = List.of(
      new Person(1, GENDER.MALE, "Miller", List.of("William")),
      new Person(2, GENDER.FEMALE, "de Beauvoir", List.of("Charlotte", "Simone", "Z.")),
      new Person(3, GENDER.FEMALE, "Monroe", null),
      new Person(4, GENDER.MALE, null, List.of("Mark", "", "F."))
    );

    try (Statement st = con.createStatement()) {
      st.execute("CREATE TYPE GENDER AS ENUM ('MALE','FEMALE')");
      st.execute("CREATE TABLE person (key int, surname text, firstnames text[], gender gender)");
    }
    return values;
  }

  @Test
  public void roundtrip() throws Exception {
    var values = prepareData();

    // dump and reload into new table
    try (TempFile tf = new TempFile();
         FileOutputStream out = new FileOutputStream(tf.file)
    ) {
      System.out.println(tf.file);
      try (PgBinaryWriter w = new PgBinaryWriter(out)) {
        for (var p : values) {
          w.startRow(4);
          w.writePInt(p.key);
          w.writeEnum(p.gender);
          w.writeString(p.surname);
          w.writeStringArray(p.firstnames);
        }
      }
      PgCopyUtils.loadBinary(con, "person", List.of("key", "gender", "surname", "firstnames"), tf.file);
    }

    // dump n read
    try (TempFile tf = new TempFile();
         FileInputStream in = new FileInputStream(tf.file)
    ) {
      System.out.println(tf.file);
      PgCopyUtils.dumpBinary(con, "SELECT key, gender, surname, firstnames FROM person", tf.file);
      try (PgBinaryReader r = new PgBinaryReader(in)) {
        for (var p : values) {
          assertTrue(r.startRow());
          assertEquals(p.key, r.readPInt());
          assertEquals(p.gender, r.readEnum(GENDER.class));
          assertEquals(p.surname, r.readString());
          assertEquals(p.firstnames, r.readStringArray());
        }
      }
    }
  }
}