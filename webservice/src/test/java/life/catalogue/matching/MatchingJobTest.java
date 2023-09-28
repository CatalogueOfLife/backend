package life.catalogue.matching;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.io.TempFile;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.EmailNotificationTemplateTest;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;

import java.util.List;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class MatchingJobTest extends EmailNotificationTemplateTest {

  @ClassRule
  public final static SqlSessionFactoryRule pg = new PgSetupRule();

  @Rule
  public final TestDataRule dataRule = TestDataRule.apple();

  UsageMatcherGlobal matcher;

  @Before
  public void setUp() throws Exception {
    matcher = mock(UsageMatcherGlobal.class);
    int dkey = dataRule.testData.key;
    doReturn(UsageMatch.empty(dkey)).when(matcher).match(anyInt(), any(), (List<? extends SimpleName>) any());
  }

  @Override
  public BackgroundJob buildJob() {
    try (TempFile tmp = new TempFile()) {
      MatchingRequest req = new MatchingRequest();
      req.setDatasetKey(dataRule.testData.key);
      req.setUpload(tmp.file);
      return new MatchingJob(req, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), matcher, new WsServerConfig());
    }
  }

  @Test
  public void testMatching() throws Exception {
    MatchingRequest req = new MatchingRequest();
    req.setDatasetKey(dataRule.testData.key);
    req.setSourceDatasetKey(dataRule.testData.key);
    var job = new MatchingJob(req, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), matcher, new WsServerConfig());
    job.run();
    assertTrue(job.isFinished());
  }
}