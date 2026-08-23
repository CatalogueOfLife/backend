package life.catalogue.jobs;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SectorImport;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.api.vocab.Users;
import life.catalogue.dao.FileMetricsSectorDao;
import life.catalogue.db.mapper.SectorImportMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;

import org.apache.ibatis.session.SqlSession;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class SectorImportRetentionJobIT {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.draftWithSectors();

  File repo;
  FileMetricsSectorDao fmDao;
  int sectorId;

  @Before
  public void init() throws Exception {
    repo = Files.createTempDirectory("clb-retention").toFile();
    repo.deleteOnExit();
    fmDao = new FileMetricsSectorDao(SqlSessionFactoryRule.getSqlSessionFactory(), repo);
  }

  /**
   * A project with no release at all has no safe cutoff, so nothing may be deleted.
   */
  @Test
  public void noReleaseDeletesNothing() {
    seed(5);
    var job = new SectorImportRetentionJob(Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(),
      fmDao, Datasets.COL, true);
    job.run();
    assertEquals(JobStatus.FINISHED, job.getStatus());
    assertEquals(0, job.getDeletableRows());
  }

  private void seed(int attempts) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      Sector s = new Sector();
      s.setDatasetKey(Datasets.COL);
      s.setSubjectDatasetKey(TestDataRule.APPLE.key);
      s.setMode(Sector.Mode.MERGE);
      s.setCreatedBy(Users.TESTER);
      s.setModifiedBy(Users.TESTER);
      session.getMapper(SectorMapper.class).create(s);
      sectorId = s.getId();

      var sim = session.getMapper(SectorImportMapper.class);
      for (int i = 1; i <= attempts; i++) {
        SectorImport si = new SectorImport();
        si.setDatasetKey(Datasets.COL);
        si.setSectorKey(sectorId);
        si.setAttempt(i);
        si.setStarted(LocalDateTime.now().minusDays(attempts - i + 1L));
        si.setCreatedBy(Users.TESTER);
        si.setNameCount(0);
        sim.create(si);
      }
    }
  }
}
