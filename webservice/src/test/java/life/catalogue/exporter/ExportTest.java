package life.catalogue.exporter;

import life.catalogue.ApiUtils;
import life.catalogue.WsServerConfig;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.TestDataRule;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

public class ExportTest {
  private static final Logger LOG = LoggerFactory.getLogger(ExportTest.class);

  WsServerConfig cfg;

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

  @Before
  public void initCfg()  {
    cfg = new WsServerConfig();
    cfg.apiURI= ApiUtils.API;
  }

  @After
  public void cleanup()  {
    LOG.info("Cleaning up download directory {}", cfg.job.downloadDir);
    FileUtils.deleteQuietly(cfg.job.downloadDir);
  }

  static void assertExportExists(File file) {
    assertTrue("Export file missing: " + file, Files.exists(file.toPath()));
  }

}