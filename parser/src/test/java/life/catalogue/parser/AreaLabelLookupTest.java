package life.catalogue.parser;

import life.catalogue.api.vocab.area.Gazetteer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AreaLabelLookupTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void bundledGazetteersWithoutDir() {
    var lookup = new AreaLabelLookup();
    assertEquals("Germany", lookup.findLabel(Gazetteer.ISO, "DE"));
    // not configured gazetteers fall through to id
    assertEquals("2", lookup.findLabel(Gazetteer.TDWG, "2"));
    assertEquals("37.4.1", lookup.findLabel(Gazetteer.FAO, "37.4.1"));
    assertNull(lookup.findLabel(Gazetteer.FAO, null));
  }

  @Test
  public void faoFromDir() throws Exception {
    File dir = tmp.newFolder();
    writeLabels(dir.toPath().resolve("fao/labels.tsv"),
      "37.4.1\tMediterranean and Black Sea - Levant\n" +
      "27\tAtlantic, Northeast\n");

    var lookup = new AreaLabelLookup(dir);
    assertEquals("Mediterranean and Black Sea - Levant", lookup.findLabel(Gazetteer.FAO, "37.4.1"));
    assertEquals("Atlantic, Northeast", lookup.findLabel(Gazetteer.FAO, "27"));
    // unknown id falls back to id itself
    assertEquals("99.99", lookup.findLabel(Gazetteer.FAO, "99.99"));
  }

  @Test
  public void mrgidFromDir() throws Exception {
    File dir = tmp.newFolder();
    writeLabels(dir.toPath().resolve("mrgid/labels.tsv"),
      "8371\tBaltic Sea\n");

    var lookup = new AreaLabelLookup(dir);
    assertEquals("Baltic Sea", lookup.findLabel(Gazetteer.MRGID, "8371"));
    assertEquals("12345", lookup.findLabel(Gazetteer.MRGID, "12345"));
  }

  @Test
  public void missingGazetteerSubdirIsOk() throws Exception {
    File dir = tmp.newFolder();
    // empty dir — no fao/iho/mrgid subdirs
    var lookup = new AreaLabelLookup(dir);
    assertEquals("37.4.1", lookup.findLabel(Gazetteer.FAO, "37.4.1"));
    assertEquals("28", lookup.findLabel(Gazetteer.IHO, "28"));
    // bundled vocabs still work
    assertEquals("Germany", lookup.findLabel(Gazetteer.ISO, "DE"));
  }

  @Test
  public void nullDirIsOk() {
    var lookup = new AreaLabelLookup(null);
    assertEquals("Germany", lookup.findLabel(Gazetteer.ISO, "DE"));
    assertEquals("37.4.1", lookup.findLabel(Gazetteer.FAO, "37.4.1"));
  }

  private static void writeLabels(Path file, String contents) throws Exception {
    Files.createDirectories(file.getParent());
    Files.writeString(file, contents);
  }
}
