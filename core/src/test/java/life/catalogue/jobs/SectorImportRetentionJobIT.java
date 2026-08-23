package life.catalogue.jobs;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SectorImport;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.api.vocab.Users;
import life.catalogue.dao.DatasetSourceDao;
import life.catalogue.dao.FileMetricsSectorDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.SectorImportMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;

import org.apache.ibatis.session.SqlSession;
import org.gbif.nameparser.api.Rank;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SectorImportRetentionJobIT {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.apple();

  File repo;
  FileMetricsSectorDao fmDao;
  int sectorId;
  int releaseKey;

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

  /**
   * The bar for the whole feature: pruning must not move a single metric.
   */
  @Test
  public void releaseMetricsUnchanged() throws Exception {
    seedWithRelease();
    var sourceDao = new DatasetSourceDao(SqlSessionFactoryRule.getSqlSessionFactory());

    var before = ApiModule.MAPPER.writeValueAsString(sourceDao.releaseMetrics(releaseKey, null, null));

    var job = new SectorImportRetentionJob(Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(),
      fmDao, Datasets.COL, false);
    job.run();
    assertEquals(JobStatus.FINISHED, job.getStatus());
    assertTrue("something should have been pruned", job.getDeletedRows() > 0);

    var after = ApiModule.MAPPER.writeValueAsString(sourceDao.releaseMetrics(releaseKey, null, null));
    assertEquals("release metrics must be byte identical after pruning", before, after);
  }

  /**
   * The attempt the project points at survives even when it predates the last release.
   */
  @Test
  public void projectCurrentSyncSurvives() {
    seedWithRelease();
    var job = new SectorImportRetentionJob(Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(),
      fmDao, Datasets.COL, false);
    job.run();

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var s = session.getMapper(SectorMapper.class).get(DSID.of(Datasets.COL, sectorId));
      var si = session.getMapper(SectorImportMapper.class).get(DSID.of(Datasets.COL, sectorId), s.getSyncAttempt());
      assertNotNull("the projects pinned attempt must survive", si);
    }
  }

  /**
   * The exact keep/delete outcome of the rule on the fixture: only attempt 3 is unpinned AND older
   * than the cutoff. 1 is the project pin, 2 the release pin, 4 and 5 postdate the release.
   */
  @Test
  public void exactRetentionOutcome() {
    seedWithRelease();
    var job = new SectorImportRetentionJob(Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(),
      fmDao, Datasets.COL, false);
    job.run();
    assertEquals(1, job.getDeletedRows());

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var sim = session.getMapper(SectorImportMapper.class);
      DSID<Integer> key = DSID.of(Datasets.COL, sectorId);
      assertNotNull("attempt 1 is the project pin", sim.get(key, 1));
      assertNotNull("attempt 2 is the release pin", sim.get(key, 2));
      assertNull("attempt 3 is unpinned and predates the release", sim.get(key, 3));
      assertNotNull("attempt 4 postdates the release", sim.get(key, 4));
      assertNotNull("attempt 5 postdates the release", sim.get(key, 5));
    }
  }

  /**
   * Every surviving row with names keeps its file; every deleted row loses it.
   */
  @Test
  public void filesMatchRows() {
    seedWithRelease();
    var job = new SectorImportRetentionJob(Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(),
      fmDao, Datasets.COL, false);
    job.run();

    DSID<Integer> key = DSID.of(Datasets.COL, sectorId);
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var sim = session.getMapper(SectorImportMapper.class);
      for (int i = 1; i <= 5; i++) {
        var si = sim.get(key, i);
        boolean fileExists = fmDao.namesFile(key, i).exists();
        if (si == null) {
          assertFalse("deleted attempt " + i + " must have no file", fileExists);
        } else if (si.getNameCount() != null && si.getNameCount() > 0) {
          assertTrue("kept attempt " + i + " with names must keep its file", fileExists);
        }
      }
    }
  }

  /**
   * A dry run reports the same deletable count a real run deletes, and changes nothing.
   */
  @Test
  public void dryRunChangesNothing() {
    seedWithRelease();
    var f = SqlSessionFactoryRule.getSqlSessionFactory();
    DSID<Integer> key = DSID.of(Datasets.COL, sectorId);

    var dry = new SectorImportRetentionJob(Users.TESTER, f, fmDao, Datasets.COL, true);
    dry.run();
    assertEquals(0, dry.getDeletedRows());
    assertEquals(0, dry.getDeletedFiles());

    try (SqlSession session = f.openSession(true)) {
      var sim = session.getMapper(SectorImportMapper.class);
      for (int i = 1; i <= 5; i++) {
        assertNotNull("dry run must not delete attempt " + i, sim.get(key, i));
        assertTrue("dry run must not delete files", fmDao.namesFile(key, i).exists());
      }
    }

    var real = new SectorImportRetentionJob(Users.TESTER, f, fmDao, Datasets.COL, false);
    real.run();
    assertEquals("dry run must predict exactly what a real run deletes",
      dry.getDeletableRows(), real.getDeletedRows());
  }

  /**
   * Running twice must delete nothing the second time.
   */
  @Test
  public void idempotent() {
    seedWithRelease();
    var f = SqlSessionFactoryRule.getSqlSessionFactory();
    var first = new SectorImportRetentionJob(Users.TESTER, f, fmDao, Datasets.COL, false);
    first.run();
    var second = new SectorImportRetentionJob(Users.TESTER, f, fmDao, Datasets.COL, false);
    second.run();
    assertEquals(0, second.getDeletedRows());
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

  /**
   * One MERGE sector with 5 attempts, oldest first, each with real names tied to the sector so
   * {@link FileMetricsSectorDao#updateNames} writes an actual, non-empty file per attempt - the apple
   * project (dataset 3) ships with no name data of its own to reuse, so a handful of minimal names are
   * inserted directly under the sector first.
   * A release is created whose `created` sits between attempt 3 and 4, and which pins attempt 2. The
   * project pins attempt 1 - the OLDEST - so the "current sync survives however old" case is genuinely
   * exercised.
   * Expected outcome: attempts 1 (project pin) and 2 (release pin) survive as pinned, 4 and 5 survive as
   * newer than the cutoff, and only attempt 3 is deletable.
   */
  private void seedWithRelease() {
    var factory = SqlSessionFactoryRule.getSqlSessionFactory();
    final LocalDateTime t0 = LocalDateTime.now().minusDays(50);
    try (SqlSession session = factory.openSession(true)) {
      var sm = session.getMapper(SectorMapper.class);
      var sim = session.getMapper(SectorImportMapper.class);
      var dm = session.getMapper(DatasetMapper.class);
      var nm = session.getMapper(NameMapper.class);

      Sector s = new Sector();
      s.setDatasetKey(Datasets.COL);
      s.setSubjectDatasetKey(TestDataRule.APPLE.key);
      s.setMode(Sector.Mode.MERGE);
      s.applyUser(Users.TESTER);
      sm.create(s);
      sectorId = s.getId();

      // a handful of real names tied to the sector, so updateNames() below has something real to write
      String[] names = {"Abies alba", "Abies balsamea", "Abies concolor"};
      for (int i = 0; i < names.length; i++) {
        Name n = TestEntityGenerator.newMinimalName(Datasets.COL, "retention-" + (i + 1), names[i], Rank.SPECIES);
        n.setSectorKey(sectorId);
        n.applyUser(Users.TESTER);
        nm.create(n);
      }

      for (int i = 1; i <= 5; i++) {
        SectorImport si = new SectorImport();
        si.setDatasetKey(Datasets.COL);
        si.setSectorKey(sectorId);
        si.setAttempt(i);
        si.setStarted(t0.plusDays(i * 5L));
        si.setCreatedBy(Users.TESTER);
        si.setNameCount(3); // matches the 3 real names created above
        sim.create(si);
        fmDao.updateNames(DSID.of(Datasets.COL, sectorId), DSID.of(Datasets.COL, sectorId), i);
      }

      // release created between attempt 3 (t0+15d) and attempt 4 (t0+20d)
      Dataset rel = new Dataset();
      rel.setTitle("Test XRelease");
      rel.setOrigin(DatasetOrigin.XRELEASE);
      rel.setSourceKey(Datasets.COL);
      rel.setType(DatasetType.TAXONOMIC);
      rel.applyUser(Users.TESTER);
      dm.create(rel);
      releaseKey = rel.getKey();
      dm.updateCreated(releaseKey, t0.plusDays(17));

      // the release pins attempt 2
      Sector rs = new Sector();
      rs.setId(sectorId);
      rs.setDatasetKey(releaseKey);
      rs.setSubjectDatasetKey(TestDataRule.APPLE.key);
      rs.setMode(Sector.Mode.MERGE);
      rs.setSyncAttempt(2);
      rs.applyUser(Users.TESTER);
      sm.createWithID(rs);

      // the project pins the OLDEST attempt
      sm.updateLastSync(DSID.of(Datasets.COL, sectorId), 1);
    }
  }
}
