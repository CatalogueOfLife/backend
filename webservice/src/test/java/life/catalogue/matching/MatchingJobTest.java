package life.catalogue.matching;

import life.catalogue.WsServerConfig;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.io.TempFile;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.EmailNotificationTemplateTest;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;

import life.catalogue.db.TestDataRule;

import org.junit.ClassRule;
import org.junit.Rule;

public class MatchingJobTest extends EmailNotificationTemplateTest {

  @ClassRule
  public final static SqlSessionFactoryRule pg = new PgSetupRule();

  @Rule
  public final TestDataRule dataRule = TestDataRule.apple();

  @Override
  public BackgroundJob buildJob() {
    try (TempFile tmp = new TempFile()) {
      MatchingRequest req = new MatchingRequest();
      req.setDatasetKey(dataRule.testData.key);
      req.setUpload(tmp.file);
      return new MatchingJob(req, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), null, new WsServerConfig());
    }
  }
}