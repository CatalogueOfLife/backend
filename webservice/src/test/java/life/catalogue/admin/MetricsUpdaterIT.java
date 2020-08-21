package life.catalogue.admin;

import life.catalogue.WsServerConfig;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class MetricsUpdaterIT {

  @ClassRule
  public final static PgSetupRule pg = new PgSetupRule();

  @Rule
  public final TestDataRule dataRule = TestDataRule.draft();

  @Test
  public void testMetricsUpdater() {
    WsServerConfig cfg = new WsServerConfig();
    MetricsUpdater updater = new MetricsUpdater(PgSetupRule.getSqlSessionFactory(), cfg, null, TestEntityGenerator.USER_ADMIN);
    updater.run();
  }

}