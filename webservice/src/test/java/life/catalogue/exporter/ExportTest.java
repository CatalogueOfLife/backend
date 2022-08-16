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

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

public class ExportTest {

  WsServerConfig cfg;
  Timer timer;

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
    MetricRegistry registry = new MetricRegistry();
    timer = registry.timer("test.timer");
  }

  @After
  public void cleanup()  {
    FileUtils.deleteQuietly(cfg.exportDir);
  }

}