package life.catalogue.common.io;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TabReaderTest {

  @Test
  public void testCsv() throws IOException {
    var r = TabReader.csv(Resources.reader("charsets/utf-8.txt"), 0);
    for (var x : r) {
      System.out.println(x);
    }
  }

  @Test
  public void roundtrip() throws IOException {
    var values = List.of(
      "Rhypholophus-\n-simulans",
      "Rhypholophus-\\\\n-simulans",
      "Limoniidae-Eriopterinae-Rhypholophus-\\n-simulans-28a397a9d",
      "Rhypholophus-\\t-simulans\\r\\nxxx",
      "Rhypholophus (\n) simulans"
    );
    StringWriter sw = new StringWriter();
    TabWriter w = new TabWriter(sw);
    int idx = 1;
    for (var x : values) {
      System.out.println(idx + " - " + x);
      w.write(new String[]{String.valueOf(idx++), x});
    }
    w.close();

    System.out.println("\n");
    System.out.println(sw);

    var sr = new StringReader(sw.toString());
    TabReader r = TabReader.tab(sr, 0);
    idx = 1;
    for (String[] row : r) {
      System.out.println(row[0] + " - " + row[1]);
      assertEquals(String.valueOf(idx), row[0]);
      assertEquals(values.get(idx-1), row[1]);
      idx++;
    }
    assertEquals(6, idx);
  }
}