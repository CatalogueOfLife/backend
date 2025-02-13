package life.catalogue.exporter;

import life.catalogue.TestConfigs;
import life.catalogue.common.io.PathUtils;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.TestDataRule;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertTrue;

public class ExportTest {
  private static final Logger LOG = LoggerFactory.getLogger(ExportTest.class);

  TestConfigs cfg;

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
    cfg = TestConfigs.build();
  }

  @After
  public void cleanup()  {
    LOG.info("Cleaning up download directory {}", cfg.job.downloadDir);
    FileUtils.deleteQuietly(cfg.getJob().downloadDir);
  }

  void assertExportExists(File file) {
    final Path path = file.toPath();
    PathUtils.printDir(cfg.getJob().downloadDir.toPath());
    if (!Files.exists(path)) {
      // we sometimes see result files not found on jenkins sometimes. give the FS a bit more time
      try {
        TimeUnit.MILLISECONDS.sleep(250);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      System.out.println("After waiting for 250ms...");
      PathUtils.printDir(cfg.getJob().downloadDir.toPath());
      assertTrue("Export file missing: " + file, Files.exists(path));
    }
  }
  
}