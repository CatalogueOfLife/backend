package life.catalogue.matching;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.io.TempFile;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.EmailNotification;
import life.catalogue.concurrent.EmailNotificationTemplateTest;
import life.catalogue.config.MailConfig;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;

import life.catalogue.db.TestDataRule;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

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

  @Test
  public void testMatching() throws Exception {
    MatchingRequest req = new MatchingRequest();
    req.setDatasetKey(dataRule.testData.key);
    req.setSourceDatasetKey(dataRule.testData.key);
    var job = new MatchingJob(req, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), null, new WsServerConfig());
    job.run();
  }
}