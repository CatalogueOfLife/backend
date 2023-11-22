package life.catalogue.pgcopy;

import life.catalogue.common.io.TempFile;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class PgBinarySplitterTest {
  enum GENDER {MALE, FEMALE}

  @Test
  public void split() throws Exception {
    final var values = new ArrayList<>(List.of(
      "Anne",
      "12345",
      "Butch Meier",
      "",
      "\t\n ",
      "Dr. Schíwågö",
      "sedolö345r6t7z8ui.ö,mnjhgztfr3wesdrfghjklöä",
      "读写汉字 - 学中文", "عة حرم ذلك الدين القيم فلاتظلموا فيهن انفسكم ",
      "14.11.2017 — 1 На поча́тку створил Бẙг небе а земи."));
    values.add(null);

    try (TempFile full = new TempFile()) {
      try (FileOutputStream out = new FileOutputStream(full.file);
           PgBinaryWriter w = new PgBinaryWriter(out)
      ) {
        System.out.println(full.file);
        int key = 1;
        while (key<1000) {
          w.startRow(4);
          w.writeInteger(key++);
          w.writePBoolean(true);
          w.writeString(values.get(key%10));
          w.writeEnum(GENDER.FEMALE);
        }
      }

      var base = new TempFile();
      int parts = 0;
      try (FileInputStream in = new FileInputStream(full.file)) {
        AtomicInteger cnt = new AtomicInteger(1);
        var splitter = new PgBinarySplitter(in, 80, () -> splitFile(full.file, cnt.getAndIncrement()));
        parts = splitter.split();
        assertEquals(13, parts);

        // try to read them individually
        int part = 1;
        while (part <= parts) {
          int recs = 0;
          try (PgBinaryReader r = new PgBinaryReader(new FileInputStream(splitFile(full.file, part)))) {
            while(r.startRow()) {
              recs++;
              var i = r.readPInt();
              assertTrue(r.readBoolean());
              var x = r.readString();
              assertEquals(GENDER.FEMALE, r.readEnum(GENDER.class));
            }
          }
          if (part == parts) {
            assertEquals(39, recs);
          } else {
            assertEquals(80, recs);
          }
          part++;
        }

      } finally {
        while (parts >= 0) {
          new File(base.file.getParentFile(), base.file.getName()+"-"+parts).delete();
          parts--;
        }
      }
    }
  }

  static File splitFile(File base, int part) {
    return new File(base.getParentFile(), base.getName()+"-"+part);
  }
}