package life.catalogue.matching;

import life.catalogue.TestConfigs;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.io.TempFile;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.EmailNotificationTemplateTest;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import life.catalogue.matching.nidx.NameIndex;

import life.catalogue.matching.nidx.NameIndexFactory;
import life.catalogue.matching.nidx.NameIndexImpl;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class MatchingJobTest extends EmailNotificationTemplateTest {

  @ClassRule
  public final static SqlSessionFactoryRule pg = new PgSetupRule();

  @Rule
  public final TestDataRule dataRule = TestDataRule.apple();

  UsageMatcherFactory matcherFactory;
  TestConfigs cfg;

  @Before
  public void setUp() throws Exception {
    this.cfg = TestConfigs.build();
    var matcher = mock(UsageMatcher.class);
    when(matcher.match(any(), anyBoolean(), anyBoolean())).thenReturn(UsageMatch.empty(0));
    matcherFactory = mock(UsageMatcherFactory.class);
    when(matcherFactory.persistent(anyInt())).thenReturn(matcher);
    when(matcherFactory.getNameIndex()).thenReturn(NameIndexFactory.passThru());
  }

  @Override
  public BackgroundJob buildJob() {
    try (TempFile tmp = new TempFile()) {
      MatchingRequest req = new MatchingRequest();
      req.setDatasetKey(dataRule.testData.key);
      req.setUpload(tmp.file);
      return new MatchingJob(req, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), matcherFactory, cfg.normalizer);
    }
  }

  @Test
  public void testMatching() throws Exception {
    MatchingRequest req = new MatchingRequest();
    req.setDatasetKey(dataRule.testData.key);
    req.setSourceDatasetKey(dataRule.testData.key);
    var job = new MatchingJob(req, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), matcherFactory, cfg.normalizer);
    job.run();
    assertTrue(job.isFinished());
  }
}