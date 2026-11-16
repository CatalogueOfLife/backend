package life.catalogue.importer;

import life.catalogue.api.vocab.DataFormat;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.io.CompressionUtil;
import life.catalogue.common.io.Resources;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class DataFormatDetectorTest {
  private static final Map<String, DataFormat> FIXTURES = Map.of(
    "dwca", DataFormat.DWCA,
    "coldp", DataFormat.COLDP,
    "acef", DataFormat.ACEF,
    "txtree", DataFormat.TEXT_TREE
  );

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void detectFormat() {
    assertEquals(DataFormat.DWCA, DataFormatDetector.detectFormat(Resources.toPath("dwca/0")));
    assertEquals(DataFormat.DWCA, DataFormatDetector.detectFormat(Resources.toPath("dwca/plazi1")));

    assertEquals(DataFormat.TEXT_TREE, DataFormatDetector.detectFormat(Resources.toPath("txtree/0")));

    assertEquals(DataFormat.COLDP, DataFormatDetector.detectFormat(Resources.toPath("coldp/0")));
    assertEquals(DataFormat.COLDP, DataFormatDetector.detectFormat(Resources.toPath("coldp/1")));

    assertEquals(DataFormat.ACEF, DataFormatDetector.detectFormat(Resources.toPath("acef/0")));
    assertEquals(DataFormat.ACEF, DataFormatDetector.detectFormat(Resources.toPath("acef/1")));
  }

  /**
   * Every import test fixture zipped, both flat and inside a wrapping folder like github archives.
   * They cover dwca without meta.xml, ColDP in a data folder, with plural file names or semicolons, ACEF in snake case.
   */
  @Test
  public void detectArchiveFixtures() throws IOException {
    int cnt = 0;
    for (var fx : FIXTURES.entrySet()) {
      File[] dirs = new File("src/test/resources/" + fx.getKey()).listFiles(File::isDirectory);
      assertNotNull(dirs);
      for (File d : dirs) {
        // a deliberately broken ACEF archive with nothing but a readme
        Optional<DataFormat> expected = d.getPath().endsWith("acef/17") ? Optional.empty() : Optional.of(fx.getValue());
        String name = fx.getKey() + "/" + d.getName();

        File flat = tmp.newFile();
        CompressionUtil.zipDir(d, flat, true);
        assertEquals(name, expected, DataFormatDetector.detectArchive(flat));

        File wrapper = tmp.newFolder();
        FileUtils.copyDirectory(d, new File(wrapper, "repo-main"));
        File wrapped = tmp.newFile();
        CompressionUtil.zipDir(wrapper, wrapped, true);
        assertEquals(name + " wrapped", expected, DataFormatDetector.detectArchive(wrapped));
        cnt++;
      }
    }
    assertTrue(cnt > 150);
  }

  /**
   * Old ACEF sources were stored as tar.gz
   */
  @Test
  public void tarGz() throws IOException {
    File f = tmp.newFile();
    try (var out = new TarArchiveOutputStream(new GZIPOutputStream(new FileOutputStream(f)))) {
      addTar(out, "AcceptedSpecies.tsv", "AcceptedTaxonID\tGenus\tSpeciesEpithet\n1\tAbies\talba\n");
      addTar(out, "Synonyms.tsv", "ID\tAcceptedTaxonID\n");
    }
    assertEquals(Optional.of(DataFormat.ACEF), DataFormatDetector.detectArchive(f));

    f = tmp.newFile();
    try (var out = new TarArchiveOutputStream(new GZIPOutputStream(new FileOutputStream(f)))) {
      addTar(out, "data/NameUsage.tsv", "col:ID\tcol:parentID\tcol:scientificName\tcol:rank\n1\t\tAbies\tgenus\n");
    }
    assertEquals(Optional.of(DataFormat.COLDP), DataFormatDetector.detectArchive(f));
  }

  private static void addTar(TarArchiveOutputStream out, String name, String content) throws IOException {
    byte[] data = content.getBytes(StandardCharsets.UTF_8);
    var e = new TarArchiveEntry(name);
    e.setSize(data.length);
    out.putArchiveEntry(e);
    out.write(data);
    out.closeArchiveEntry();
  }

  /**
   * A single uncompressed file is stored as it was uploaded or downloaded, named NNNN.archive.
   * The import reads it as a DwC core if it is a csv file, see CsvReader.ARCHIVE_SUFFIX.
   */
  @Test
  public void singleFile() throws IOException {
    File f = file("0001.archive", "taxonID,scientificName,taxonRank,genus,scientificNameAuthorship,NATURA2000_CODE\r\n" +
      "1455,Monachus monachus,Species,Monachus,\"(Hermann, 1779)\",1366\r\n");
    assertEquals(Optional.of(DataFormat.DWCA), DataFormatDetector.detectArchive(f));

    f = file("0002.archive", "Animalia [kingdom]\n  Chordata [phylum]\n    Mammalia [class]\n");
    assertEquals(Optional.of(DataFormat.TEXT_TREE), DataFormatDetector.detectArchive(f));

    // gzipped
    f = tmp.newFile("0003.archive");
    try (var out = new GZIPOutputStream(new FileOutputStream(f))) {
      out.write("Animalia [kingdom]\n  Chordata [phylum]\n".getBytes(StandardCharsets.UTF_8));
    }
    assertEquals(Optional.of(DataFormat.TEXT_TREE), DataFormatDetector.detectArchive(f));
  }

  /**
   * metadata.yaml and eml.xml are used by several formats, two plain text files are no text tree.
   */
  @Test
  public void undetected() throws IOException {
    File dir = tmp.newFolder();
    write(new File(dir, "metadata.yaml"), "title: Bla\n");
    write(new File(dir, "eml.xml"), "<eml/>\n");
    File zip = tmp.newFile();
    CompressionUtil.zipDir(dir, zip, true);
    assertEquals(Optional.empty(), DataFormatDetector.detectArchive(zip));

    write(new File(dir, "a.txt"), "Abies\n");
    write(new File(dir, "b.txt"), "Pinus\n");
    zip = tmp.newFile();
    CompressionUtil.zipDir(dir, zip, true);
    assertEquals(Optional.empty(), DataFormatDetector.detectArchive(zip));
  }

  /**
   * One tree file besides metadata and a readme is a text tree, as in github repositories.
   */
  @Test
  public void singleTreeFile() throws IOException {
    File dir = tmp.newFolder();
    write(new File(dir, "metadata.yaml"), "title: Bla\n");
    write(new File(dir, "README.md"), "# Bla\n");
    write(new File(dir, "readme.txt"), "Bla\n");
    write(new File(dir, "benton.txtree"), "Chordata [phylum]\n");
    File zip = tmp.newFile();
    CompressionUtil.zipDir(dir, zip, true);
    assertEquals(Optional.of(DataFormat.TEXT_TREE), DataFormatDetector.detectArchive(zip));
  }

  @Test
  public void coldpCoreFile() {
    assertEquals(ColdpTerm.NameUsage, DataFormatDetector.coldpCoreFile("name_usage.tsv"));
    assertEquals(ColdpTerm.NameUsage, DataFormatDetector.coldpCoreFile("NameUsage.csv"));
    assertEquals(ColdpTerm.Name, DataFormatDetector.coldpCoreFile("names.tsv"));
    assertEquals(ColdpTerm.Taxon, DataFormatDetector.coldpCoreFile("taxa.txt"));
    assertEquals(ColdpTerm.Reference, DataFormatDetector.coldpCoreFile("References.csv"));
    assertNull(DataFormatDetector.coldpCoreFile("Distribution.tsv"));
    assertNull(DataFormatDetector.coldpCoreFile("NameUsage.xlsx"));
    assertNull(DataFormatDetector.coldpCoreFile("metadata.yaml"));
  }

  @Test
  public void headers() throws IOException {
    assertEquals(List.of("ID", "parentID", "scientificName"), header("﻿col:ID\tcol:parentID\tcol:scientificName"));
    assertEquals(List.of("ID", "basionymId", "scientificName"), header("ID;basionymId;\"scientificName\""));

    assertTrue(DataFormatDetector.isColdpHeader(header("ID,parentID,scientificName,authorship,rank,status"), ColdpTerm.NameUsage));
    // denormalised dwc taxon file
    assertFalse(DataFormatDetector.isColdpHeader(header("taxonID\tscientificName\tscientificNameAuthorship\ttaxonRank\tkingdom\tphylum\tclass\torder\tfamily\tgenus"), ColdpTerm.Taxon));
    assertFalse(DataFormatDetector.isColdpHeader(header("foo,bar,ID"), ColdpTerm.Name));
    assertFalse(DataFormatDetector.isColdpHeader(List.of(), ColdpTerm.Name));
  }

  private static List<String> header(String line) throws IOException {
    return DataFormatDetector.firstLine(new ByteArrayInputStream((line + "\n1,2,3\n").getBytes(StandardCharsets.UTF_8)));
  }

  private File file(String name, String content) throws IOException {
    File f = tmp.newFile(name);
    write(f, content);
    return f;
  }

  private static void write(File f, String content) throws IOException {
    Files.writeString(f.toPath(), content);
  }
}
