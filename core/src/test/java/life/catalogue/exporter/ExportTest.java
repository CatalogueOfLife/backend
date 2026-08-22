package life.catalogue.exporter;

import life.catalogue.TestConfigs;
import life.catalogue.api.model.Media;
import life.catalogue.api.vocab.License;
import life.catalogue.api.vocab.MediaType;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.mapper.MediaMapper;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.ibatis.session.SqlSession;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ExportTest {
  private static final Logger LOG = LoggerFactory.getLogger(ExportTest.class);

  static TestConfigs cfg;

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule;

  public ExportTest() {
    this.testDataRule = TestDataRule.apple();
  }

  public ExportTest(TestDataRule.TestData testData) {
    this.testDataRule = new TestDataRule(testData);
  }

  @BeforeClass
  public static void initCfg()  {
    cfg = TestConfigs.build();
    cfg.db = PgSetupRule.getCfg();
  }

  @AfterClass
  public static void cleanup()  {
    LOG.info("Cleaning up test directories");
    cfg.removeCfgDirs();
  }

  void assertExportExists(File file) {
    final Path path = file.toPath();
    assertTrue("Export file missing: " + file, Files.exists(path));
  }

  /**
   * Attaches a single media item to an existing taxon so exports have something to write.
   */
  Media insertMedia(int datasetKey, String taxonID, MediaType type, String format) {
    Media m = new Media();
    m.setDatasetKey(datasetKey);
    m.setUrl(URI.create("https://example.org/media/" + taxonID + ".jpg"));
    m.setLink(URI.create("https://example.org/page/" + taxonID));
    m.setType(type);
    m.setFormat(format);
    m.setTitle("Picture of " + taxonID);
    m.setCaptured(LocalDate.of(2020, 1, 15));
    m.setCapturedBy("Carl Linnaeus");
    m.setLicense(License.CC0);
    m.setRemarks("nice shot");
    m.applyUser(Users.TESTER);
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      session.getMapper(MediaMapper.class).create(m, taxonID);
    }
    return m;
  }

  /**
   * Reads a single entry from a zipped export archive as a string.
   */
  String readArchiveEntry(File archive, String entryName) throws IOException {
    try (ZipFile zip = new ZipFile(archive)) {
      ZipEntry entry = zip.getEntry(entryName);
      assertNotNull("Missing archive entry " + entryName, entry);
      try (InputStream in = zip.getInputStream(entry)) {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
    }
  }

  /**
   * @return the header cells of a tabular archive entry as prefixed term names, e.g. "col:type" or "dc:identifier"
   */
  List<String> readArchiveHeader(File archive, String entryName) throws IOException {
    String[] lines = readArchiveLines(archive, entryName);
    return Arrays.asList(lines[0].split("\t", -1));
  }

  /**
   * Reads a tabular archive entry into a list of rows, each keyed by the prefixed term name
   * as given in the header, e.g. "col:type" or "dc:identifier".
   */
  List<Map<String, String>> readArchiveRows(File archive, String entryName) throws IOException {
    String[] lines = readArchiveLines(archive, entryName);
    String[] header = lines[0].split("\t", -1);
    List<Map<String, String>> rows = new ArrayList<>();
    for (int r = 1; r < lines.length; r++) {
      if (lines[r].isEmpty()) continue;
      String[] cells = lines[r].split("\t", -1);
      Map<String, String> row = new LinkedHashMap<>();
      for (int i = 0; i < header.length; i++) {
        row.put(header[i], i < cells.length ? cells[i] : null);
      }
      rows.add(row);
    }
    return rows;
  }

  private String[] readArchiveLines(File archive, String entryName) throws IOException {
    String content = readArchiveEntry(archive, entryName);
    String[] lines = content.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);
    assertTrue("Empty archive entry " + entryName, lines.length > 0 && !lines[0].isEmpty());
    return lines;
  }
}