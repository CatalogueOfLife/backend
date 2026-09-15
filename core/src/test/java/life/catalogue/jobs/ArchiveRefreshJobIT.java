package life.catalogue.jobs;

import life.catalogue.api.model.DSID;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.mapper.ArchivedNameUsageMapper;
import life.catalogue.db.mapper.ArchivedNameUsageMatchMapper;
import life.catalogue.junit.NameMatchingRule;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class ArchiveRefreshJobIT {

  @ClassRule
  public static SqlSessionFactoryRule pgSetupRule = new PgSetupRule();

  // matchAll=false: no archived name is matched before the job runs, so every archive match comes from its rematch
  @Rule
  public final TestRule chain = RuleChain
    .outerRule(TestDataRule.archive())
    .around(new NameMatchingRule(SqlSessionFactoryRule::getSqlSessionFactory, false, false));

  ArchiveRefreshJob job(boolean dryRun) {
    return new ArchiveRefreshJob(Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), NameMatchingRule.getIndex(),
      Datasets.COL, dryRun);
  }

  @Test
  public void refreshesAndRematches() throws Exception {
    var job = job(false);
    job.run();
    assertEquals("job failed: " + job.getError(), JobStatus.FINISHED, job.getStatus());
    assertTrue(job.getStep(), job.getStep().startsWith("3 releases"));

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var anum = session.getMapper(ArchivedNameUsageMapper.class);
      assertEquals("Miller", anum.get(DSID.of(Datasets.COL, "A")).getName().getAuthorship());
      assertEquals("Pinus mugo", anum.get(DSID.of(Datasets.COL, "E")).getName().getScientificName());
      var amm = session.getMapper(ArchivedNameUsageMatchMapper.class);
      for (String id : List.of("A", "B", "C", "D", "E")) {
        assertNotNull("archived name " + id + " was not matched", amm.get(DSID.of(Datasets.COL, id)));
      }
    }
  }

  @Test
  public void dryRunWritesNothing() throws Exception {
    var job = job(true);
    job.run();
    assertEquals("job failed: " + job.getError(), JobStatus.FINISHED, job.getStatus());
    assertTrue(job.getStep(), job.getStep().startsWith("dry run"));
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      assertNull(session.getMapper(ArchivedNameUsageMapper.class).get(DSID.of(Datasets.COL, "D")));
    }
  }
}
