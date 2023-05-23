package life.catalogue.matching;

import life.catalogue.WsServerConfig;
import life.catalogue.api.vocab.Users;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.EmailNotificationTemplateTest;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

public class NidxExportJobIT {

  @ClassRule
  public final static SqlSessionFactoryRule pg = new PgSetupRule();

  @Rule
  public final TestDataRule dataRule = TestDataRule.nidx();

  @Test
  public void export() {
    var job = new NidxExportJob(List.copyOf(dataRule.testData.datasetKeys), 1, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), new WsServerConfig());
    job.run();
  }
}