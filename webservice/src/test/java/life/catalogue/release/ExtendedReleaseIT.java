package life.catalogue.release;

import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.Users;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.db.NameMatchingRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DatasetPartitionMapper;

import java.io.IOException;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;

public class ExtendedReleaseIT extends ProjectBaseIT {

  final static TestDataRule.TestData XCOL_DATA = new TestDataRule.TestData("xcol", 3, 1, 2,
    Map.of(
      "sector", Map.of("created_by", 100, "modified_by", 100)
    ),3,11,12,13);
  final int projectKey = XCOL_DATA.key;

  IdProvider provider;
  NameMatchingRule matchingRule = new NameMatchingRule();
  private ReleaseConfig cfg;

  @Rule
  public final TestRule chain = RuleChain
    .outerRule(new TestDataRule(XCOL_DATA))
    .around(matchingRule);


  @Before
  public void init2() throws IOException {
    cfg = new ReleaseConfig();
    provider = new IdProvider(projectKey, 1, -1, cfg, PgSetupRule.getSqlSessionFactory());
  }

  @After
  public void destroy2() {
    org.apache.commons.io.FileUtils.deleteQuietly(cfg.reportDir);
  }


  @Test
  public void release() throws Exception {
    var xrel = projectCopyFactory.buildExtendedRelease(13, Users.TESTER);
    xrel.run();

    assertEquals(ImportState.FINISHED, xrel.getMetrics().getState());
  }

}