package life.catalogue.exporter;

import life.catalogue.TestConfigs;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.TestDataRule;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
}