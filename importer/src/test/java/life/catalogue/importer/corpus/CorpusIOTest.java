package life.catalogue.importer.corpus;

import java.io.StringWriter;

import org.junit.Test;

import static org.junit.Assert.*;

public class CorpusIOTest {

  @Test
  public void roundTrip() throws Exception {
    StringWriter w = new StringWriter();
    CorpusIO.writeRow(w, "a\tb", null, "c\\nd", "line\nbreak", 12, "");
    String line = w.toString();
    assertEquals("a\\tb\t\\N\tc\\\\nd\tline\\nbreak\t12\t\\N\n", line);
    assertArrayEquals(new String[]{"a\tb", null, "c\\nd", "line\nbreak", "12", null},
      CorpusIO.split(line.substring(0, line.length() - 1), 6));
  }

  @Test
  public void splitPads() {
    assertArrayEquals(new String[]{"dwc:Taxon", "Abies", null, null, null}, CorpusIO.split("dwc:Taxon\tAbies", 5));
    assertArrayEquals(new String[]{"dwc:Taxon", null, "Mill.", null, null}, CorpusIO.split("dwc:Taxon\t\tMill.", 5));
  }
}
