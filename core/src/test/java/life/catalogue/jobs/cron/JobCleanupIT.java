package life.catalogue.jobs.cron;

import life.catalogue.api.model.JobInfo;
import life.catalogue.api.vocab.JobLane;
import life.catalogue.api.vocab.JobPriority;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.api.vocab.Users;
import life.catalogue.concurrent.JobConfig;
import life.catalogue.db.mapper.JobMapper;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.UUID;

import org.apache.ibatis.session.SqlSession;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The cleanup removes a job record and both of its logs together. They are addressed by the job key
 * alone, so a row deleted without its logs leaves files nothing can ever reach again.
 */
public class JobCleanupIT {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.empty();

  JobConfig cfg;

  @Before
  public void init() throws Exception {
    cfg = new JobConfig();
    cfg.logDir = Files.createTempDirectory("clb-joblogs").toFile();
    cfg.downloadDir = Files.createTempDirectory("clb-jobdownloads").toFile();
    cfg.logDir.deleteOnExit();
    cfg.downloadDir.deleteOnExit();
    cfg.retentionKeepPerClass = 0;
  }

  /**
   * A finished job of the given age with both of its log files on disk.
   */
  private UUID seed(String jobClass, int daysAgo) throws IOException {
    UUID key = UUID.randomUUID();
    JobInfo j = new JobInfo();
    j.setKey(key);
    j.setJob(jobClass);
    j.setLane(JobLane.DEFAULT);
    j.setStatus(JobStatus.FINISHED);
    j.setPriority(JobPriority.MEDIUM);
    j.setCreatedBy(Users.TESTER);
    j.setCreated(LocalDateTime.now().minusDays(daysAgo));
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      session.getMapper(JobMapper.class).create(j);
    }
    for (File f : new File[]{cfg.jobLog(key), cfg.downloadLogFile(key)}) {
      Files.createDirectories(f.getParentFile().toPath());
      Files.write(f.toPath(), new byte[]{1, 2, 3});
      assertTrue("fixture: " + f + " must exist", f.exists());
    }
    return key;
  }

  private void assertLogs(UUID key, boolean present) {
    assertEquals("live log of " + key, present, cfg.jobLog(key).exists());
    assertEquals("download log of " + key, present, cfg.downloadLogFile(key).exists());
  }

  private void cleanup() {
    new JobCleanup(SqlSessionFactoryRule.getSqlSessionFactory(), cfg).run();
  }

  private JobInfo get(UUID key) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      return session.getMapper(JobMapper.class).get(key);
    }
  }

  @Test
  public void deletesLogsOfReapedJobsOnly() throws Exception {
    var doomed = seed("AlphaJob", 200);
    var young = seed("AlphaJob", 2);

    cleanup();

    assertNull("the old record is gone", get(doomed));
    assertNotNull("the young record stays", get(young));
    assertLogs(doomed, false);
    assertLogs(young, true);
  }

  /**
   * A missing log must not stop the sweep - a job that never logged anything is normal.
   */
  @Test
  public void toleratesMissingLogs() throws Exception {
    var doomed = seed("BetaJob", 200);
    assertTrue(cfg.jobLog(doomed).delete());

    cleanup();

    assertNull(get(doomed));
    assertLogs(doomed, false);
  }
}
