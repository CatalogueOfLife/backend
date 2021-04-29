package life.catalogue.exporter;

import life.catalogue.ApiUtils;
import life.catalogue.WsServerConfig;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;

public class ExporterTest {

  WsServerConfig cfg;

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule;

  public ExporterTest() {
    this.testDataRule = TestDataRule.apple();
  }

  public ExporterTest(TestDataRule.TestData testData) {
    this.testDataRule = new TestDataRule(testData);
  }

  @Before
  public void initCfg()  {
    cfg = new WsServerConfig();
    cfg.apiURI= ApiUtils.API;
  }

  @After
  public void cleanup()  {
    FileUtils.deleteQuietly(cfg.exportDir);
  }

}