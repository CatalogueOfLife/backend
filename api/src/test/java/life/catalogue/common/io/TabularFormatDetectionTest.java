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

public class TabularFormatDetectionTest {
  private static final String NAMES = """
    ACANTHACEAE
    ADN Suelo
    Aaroniella
    Aaroniella badonneli
    Aaroniella badonneli (Danks, 1950)
    Aaronsohnia factorovskyi Warb. & Eig.
    Aaronsohnia pubescens (Desf.) K.Bremer & Humphries
    """;

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  private File write(String content) throws IOException {
    File f = folder.newFile();
    Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
    return f;
  }

  private TabularFormat detect(String content) throws IOException {
    return TabularFormatDetection.detectFormat(write(content), StandardCharsets.UTF_8);
  }

  @Test
  public void detectTsv() throws IOException {
    assertEquals(TabularFormat.TSV, detect("id\tscientificName\tauthorship\n1\tAbies alba\tMiller, 1891\n2\tPinus sylvestris\tL.\n"));
  }

  @Test
  public void detectCsv() throws IOException {
    assertEquals(TabularFormat.CSV, detect("id,scientificName,authorship\n1,Abies alba,Mill.\n2,Pinus sylvestris,L.\n"));
  }

  @Test
  public void csvWithCommasInValues() throws IOException {
    // commas inside quoted values are not delimiters
    assertEquals(TabularFormat.CSV, detect("id,name\n1,\"Müller, 1758\"\n2,\"Koch, 1845\"\n"));
  }

  @Test
  public void blankLinesIgnored() throws IOException {
    assertEquals(TabularFormat.CSV, detect("id,name\n\n1,Abies\n  \n2,Pinus\n"));
  }

  @Test(expected = IOException.class)
  public void emptyFile() throws IOException {
    detect("");
  }

  @Test
  public void singleColumn() throws IOException {
    assertEquals(TabularFormat.TSV, detect("justaplainword\nanotherword\n"));
  }

  /**
   * https://github.com/CatalogueOfLife/checklistbank/issues/1730
   */
  @Test
  public void singleColumnWithCommas() throws IOException {
    assertEquals(TabularFormat.TSV, detect("scientificName\n" + NAMES));
    // no header, first name already has a comma
    assertEquals(TabularFormat.TSV, detect("Abies alba Mill., 1768\n" + NAMES));
  }

  @Test
  public void tsvWithCommasInValues() throws IOException {
    assertEquals(TabularFormat.TSV, detect("col1\tcol2\nfoo, bar\tbaz\n"));
  }
}
