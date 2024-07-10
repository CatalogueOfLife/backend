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

public class ExportTest {

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
    FileUtils.deleteQuietly(cfg.job.downloadDir);
  }

}