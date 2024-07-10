package life.catalogue.matching;

import life.catalogue.WsServerConfig;
import life.catalogue.api.vocab.Users;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.EmailNotificationTemplateTest;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.util.List;

import org.junit.ClassRule;
import org.junit.Rule;

public class NidxExportJobTest extends EmailNotificationTemplateTest {

  @ClassRule
  public final static SqlSessionFactoryRule pg = new PgSetupRule();

  @Rule
  public final TestDataRule dataRule = TestDataRule.apple();

  @Override
  public BackgroundJob buildJob() {
    return new NidxExportJob(List.copyOf(dataRule.testData.datasetKeys), 1, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), new WsServerConfig());
  }
}