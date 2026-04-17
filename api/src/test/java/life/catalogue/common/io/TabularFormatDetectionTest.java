package life.catalogue.common.io;

import life.catalogue.api.vocab.TabularFormat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TabularFormatDetectionTest {

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  private File write(String content) throws IOException {
    File f = folder.newFile();
    Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
    return f;
  }

  @Test
  public void detectTsv() throws IOException {
    File f = write("id\tscientificName\tauthorship\n1\tAbies alba\tMiller, 1891\n2\tPinus sylvestris\tL.\n");
    assertEquals(TabularFormat.TSV, TabularFormatDetection.detectFormat(f, StandardCharsets.UTF_8));
  }

  @Test
  public void detectCsv() throws IOException {
    File f = write("id,scientificName,authorship\n1,Abies alba,Mill.\n2,Pinus sylvestris,L.\n");
    assertEquals(TabularFormat.CSV, TabularFormatDetection.detectFormat(f, StandardCharsets.UTF_8));
  }

  @Test
  public void csvWithCommasInValues() throws IOException {
    // commas in quoted values still dominate over no tabs
    File f = write("id,name\n1,\"Müller, 1758\"\n2,\"Koch, 1845\"\n");
    assertEquals(TabularFormat.CSV, TabularFormatDetection.detectFormat(f, StandardCharsets.UTF_8));
  }

  @Test(expected = IOException.class)
  public void emptyFile() throws IOException {
    File f = write("");
    assertNull(TabularFormatDetection.detectFormat(f, StandardCharsets.UTF_8));
  }

  @Test(expected = IOException.class)
  public void noDelimiters() throws IOException {
    File f = write("justaplainword\nanotherword\n");
    assertNull(TabularFormatDetection.detectFormat(f, StandardCharsets.UTF_8));
  }

  @Test
  public void singleColumnTsv() throws IOException {
    // one tab per line beats zero commas
    File f = write("col1\tcol2\nfoo\tbar\n");
    assertEquals(TabularFormat.TSV, TabularFormatDetection.detectFormat(f, StandardCharsets.UTF_8));
  }
}
