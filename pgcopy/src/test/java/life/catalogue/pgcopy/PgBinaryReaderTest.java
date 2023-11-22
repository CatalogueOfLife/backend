package life.catalogue.pgcopy;

import life.catalogue.common.io.TempFile;

import org.junit.Test;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class PgBinaryReaderTest {
  enum GENDER {MALE, FEMALE}
  @Test
  public void roundtrip() throws Exception {
    final var values = new ArrayList<>(List.of("Anne", "Bernd", "Bux Meier", "", "\t\n ", "Dr. Schíwågö"));
    final var authors = List.of("L.", "DC.", "H.P.Lovecraft");
    values.add(null);

    try (TempFile tf = new TempFile()) {
      try(FileOutputStream out = new FileOutputStream(tf.file);
          PgBinaryWriter w = new PgBinaryWriter(out)
      ) {
        int key = 1;
        for (String x : values) {
          w.startRow(4);
          w.writePInt(key++);
          w.writeString(x);
          w.writeEnum(GENDER.MALE);
          w.writeStringArray(authors);
        }
      }

      try(FileInputStream in = new FileInputStream(tf.file);
          PgBinaryReader r = new PgBinaryReader(in)
      ) {
        int key = 1;
        for (String x : values) {
          assertTrue(r.startRow());
          assertEquals(key++, r.readPInt());
          assertEquals(x, r.readString());
          assertEquals(GENDER.MALE, r.readEnum(GENDER.class));
          assertEquals(authors, r.readStringArray());
        }
      }
    }
  }
}