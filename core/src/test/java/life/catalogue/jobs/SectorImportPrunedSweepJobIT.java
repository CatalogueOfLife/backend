package life.catalogue.jobs;

import life.catalogue.api.model.DSID;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.api.vocab.Users;
import life.catalogue.dao.FileMetricsSectorDao;
import life.catalogue.db.mapper.SectorImportPrunedMapper;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.ibatis.session.SqlSession;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The migration deletes sector_import rows in SQL and records them in sector_import_pruned so their
 * names files can still be found afterwards. This job is the only thing that ever reads that table.
 */
public class SectorImportPrunedSweepJobIT {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.apple();

  File repo;
  FileMetricsSectorDao fmDao;

  @Before
  public void init() throws Exception {
    repo = Files.createTempDirectory("clb-sweep").toFile();
    repo.deleteOnExit();
    fmDao = new FileMetricsSectorDao(SqlSessionFactoryRule.getSqlSessionFactory(), repo);
    exec("DROP TABLE IF EXISTS sector_import_pruned");
  }

  private void exec(String sql) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      session.getConnection().createStatement().execute(sql);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /** Recreates what the migration leaves behind: the scratch table plus the orphaned files. */
  private void seed(int sectorKey, int attempts) throws IOException {
    exec("CREATE TABLE sector_import_pruned (dataset_key INTEGER NOT NULL, sector_key INTEGER NOT NULL,"
         + " attempt INTEGER NOT NULL, PRIMARY KEY (dataset_key, sector_key, attempt))");
    for (int i = 1; i <= attempts; i++) {
      exec("INSERT INTO sector_import_pruned VALUES (" + Datasets.COL + "," + sectorKey + "," + i + ")");
      File f = fmDao.namesFile(DSID.of(Datasets.COL, sectorKey), i);
      Files.createDirectories(f.getParentFile().toPath());
      Files.write(f.toPath(), new byte[0]);
      assertTrue("fixture: names file for attempt " + i + " must exist", f.exists());
    }
  }

  private SectorImportPrunedSweepJob run(boolean dryRun) {
    var job = new SectorImportPrunedSweepJob(Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), fmDao, dryRun);
    job.run();
    assertEquals(JobStatus.FINISHED, job.getStatus());
    return job;
  }

  private int remaining() {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      return session.getMapper(SectorImportPrunedMapper.class).count();
    }
  }

  /**
   * The table is dropped once the sweep has run, so the job has to cope with it being gone rather than
   * failing every time it is triggered afterwards.
   */
  @Test
  public void noTableIsNotAnError() {
    var job = run(false);
    assertEquals("nothing to sweep", job.getStep());
  }

  @Test
  public void dryRunChangesNothing() throws Exception {
    seed(1, 3);
    var job = run(true);

    assertEquals(3, remaining());
    for (int i = 1; i <= 3; i++) {
      assertTrue("dry run must not delete files", fmDao.namesFile(DSID.of(Datasets.COL, 1), i).exists());
    }
    assertTrue(job.getStep().startsWith("DRY RUN: examined 3, 3 files deletable"));
  }

  @Test
  public void sweepsFilesAndEmptiesTheTable() throws Exception {
    seed(1, 3);
    var job = run(false);

    assertEquals("a real run must consume the table so it can be dropped", 0, remaining());
    for (int i = 1; i <= 3; i++) {
      assertFalse("names file must be gone", fmDao.namesFile(DSID.of(Datasets.COL, 1), i).exists());
    }
    assertEquals("examined 3, 3 files deleted", job.getStep());
  }

  /**
   * Not every pruned attempt wrote a names file - a sync that produced no names never creates one.
   * A missing file is simply not counted, and must not be reported as a failure.
   */
  @Test
  public void missingFilesAreNotFailures() throws Exception {
    seed(1, 3);
    assertTrue(fmDao.namesFile(DSID.of(Datasets.COL, 1), 2).delete());

    var job = run(false);
    assertEquals(0, remaining());
    assertEquals("examined 3, 2 files deleted", job.getStep());
  }

  /** A real run removes each page as it goes, so re-running it finds nothing left to do. */
  @Test
  public void isResumable() throws Exception {
    seed(1, 3);
    run(false);
    assertEquals(0, remaining());

    var again = run(false);
    assertEquals(0, remaining());
    assertEquals("examined 0, 0 files deleted", again.getStep());
  }
}
