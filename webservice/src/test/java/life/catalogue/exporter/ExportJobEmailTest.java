package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.Users;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.EmailNotificationTemplateTest;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import org.gbif.nameparser.api.Rank;

import org.junit.ClassRule;
import org.junit.Rule;

public class ExportJobEmailTest extends EmailNotificationTemplateTest {

  @ClassRule
  public final static SqlSessionFactoryRule pg = new PgSetupRule();

  @Rule
  public final TestDataRule dataRule = TestDataRule.apple();

  @Override
  public BackgroundJob buildJob() {
    ExportRequest req = new ExportRequest();
    req.setDatasetKey(dataRule.testData.key);
    req.setRoot(SimpleName.sn(Rank.FAMILY, "Asteraceae"));
    return new ColdpExtendedExport(req, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), new WsServerConfig(), null);
  }
}