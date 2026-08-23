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
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;

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

    // sector_import.dataset_key has no FK to dataset (sector imports are kept for deleted sectors), so
    // TestDataRule's "TRUNCATE dataset CASCADE" never reaches it and its own truncate list skips it too.
    // Sector ids for COL are recycled by every test (their per-dataset sequence is reset on each @Rule
    // teardown/setup), so leftover sector_import rows from a previous test collide with this test's
    // attempt=1..5 inserts on the (dataset_key, sector_key, attempt) primary key. Clear them here, after
    // the @Rule has done its own setup (JUnit runs @Rule before @Before), so every test starts clean.
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      session.getMapper(SectorImportMapper.class).deleteByDataset(Datasets.COL);
    }
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
   * The bar for the whole feature: pruning must not move a single metric. Covers both goal-6 metrics -
   * releaseMetrics, and sourceMetrics in both its release-path and project-path (current=true) variants,
   * which run genuinely different queries.
   */
  @Test
  public void releaseMetricsUnchanged() throws Exception {
    seedWithRelease();
    var sourceDao = new DatasetSourceDao(SqlSessionFactoryRule.getSqlSessionFactory());

    var beforeRelease = ApiModule.MAPPER.writeValueAsString(sourceDao.releaseMetrics(releaseKey, null, null));
    var beforeReleaseSource = ApiModule.MAPPER.writeValueAsString(sourceDao.sourceMetrics(releaseKey, TestDataRule.APPLE.key, null));
    var beforeProjectSource = ApiModule.MAPPER.writeValueAsString(sourceDao.sourceMetrics(Datasets.COL, TestDataRule.APPLE.key, null));

    var job = new SectorImportRetentionJob(Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(),
      fmDao, Datasets.COL, false);
    job.run();
    assertEquals(JobStatus.FINISHED, job.getStatus());
    assertTrue("something should have been pruned", job.getDeletedRows() > 0);

    var afterRelease = ApiModule.MAPPER.writeValueAsString(sourceDao.releaseMetrics(releaseKey, null, null));
    assertEquals("release metrics must be byte identical after pruning", beforeRelease, afterRelease);

    var afterReleaseSource = ApiModule.MAPPER.writeValueAsString(sourceDao.sourceMetrics(releaseKey, TestDataRule.APPLE.key, null));
    assertEquals("release-path source metrics must be byte identical after pruning", beforeReleaseSource, afterReleaseSource);

    var afterProjectSource = ApiModule.MAPPER.writeValueAsString(sourceDao.sourceMetrics(Datasets.COL, TestDataRule.APPLE.key, null));
    assertEquals("project-path source metrics must be byte identical after pruning", beforeProjectSource, afterProjectSource);
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
   * Restores the round-trip coverage FileMetricsSectorDaoTest lost when it was rewritten off
   * {@link life.catalogue.dao.FileMetricsDaoTestBase} (see its {@code roundtripNames}): updateNames must
   * write the real name strings for a sector attempt, and getNames must read exactly those back - no
   * retention run involved here, this only exercises the DAO the job's empty-file handling builds on.
   */
  @Test
  public void namesRoundtrip() {
    seedWithRelease();
    var names = fmDao.getNames(DSID.of(Datasets.COL, sectorId), 1).toList();
    assertEquals(List.of("Abies alba", "Abies balsamea", "Abies concolor"), names);
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
   * Every surviving row with names keeps its file; every deleted row loses it. Attempt 6 additionally
   * covers the KEPT-but-empty case: its row survives (postdates the release) yet its stray names file
   * must still be removed, since a zero name_count means the file carries nothing the metrics row does
   * not (see SectorImportRetentionJob's empty-file deletion for kept rows).
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
        } else {
          assertEquals("kept attempt " + i + " must still have its name_count", (Integer) 3, si.getNameCount());
          assertTrue("kept attempt " + i + " with names must keep its file", fileExists);
        }
      }

      var si6 = sim.get(key, 6);
      assertNotNull("attempt 6 postdates the release and must be kept", si6);
      assertEquals("attempt 6's persisted name_count must be untouched", (Integer) 0, si6.getNameCount());
      assertFalse("a kept but zero-name attempt must have its stray file removed",
        fmDao.namesFile(key, 6).exists());
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

    assertEquals(1, dry.getDeletableRows());
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
    assertEquals(1, first.getDeletedRows());
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
   * A 6th attempt is added well after the release, with a persisted name_count of 0 and a stray names
   * file manufactured directly on disk (a real zero-name attempt never gets one via updateNames) -
   * exercising the empty-file deletion the job also applies to rows it KEEPS.
   * Expected outcome: attempts 1 (project pin) and 2 (release pin) survive as pinned, 4 and 5 survive as
   * newer than the cutoff, only attempt 3 is deletable, and attempt 6 survives as KEPT but loses its
   * stray file.
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

      // attempt 6: postdates the release by a wide margin, so it is KEPT regardless of any pin, but its
      // persisted metrics record zero names. A genuine zero-name attempt never gets a names file written
      // via updateNames, so this one is manufactured directly to simulate a stray leftover file - the
      // retention job must remove it even though the row itself survives (job.java:169-175).
      SectorImport si6 = new SectorImport();
      si6.setDatasetKey(Datasets.COL);
      si6.setSectorKey(sectorId);
      si6.setAttempt(6);
      si6.setStarted(t0.plusDays(30L));
      si6.setCreatedBy(Users.TESTER);
      si6.setNameCount(0);
      sim.create(si6);
      File strayFile = fmDao.namesFile(DSID.of(Datasets.COL, sectorId), 6);
      try {
        Files.createDirectories(strayFile.getParentFile().toPath());
        Files.write(strayFile.toPath(), new byte[0]);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      assertTrue("fixture setup: the stray file for attempt 6 must exist before the job runs", strayFile.exists());

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
      // the fixture is only meaningful if the backdate actually persisted - self-verify like the pins below
      assertEquals(t0.plusDays(17), dm.get(releaseKey).getCreated());

      // the release pins attempt 2
      Sector rs = new Sector();
      rs.setId(sectorId);
      rs.setDatasetKey(releaseKey);
      rs.setSubjectDatasetKey(TestDataRule.APPLE.key);
      rs.setMode(Sector.Mode.MERGE);
      rs.applyUser(Users.TESTER);
      sm.createWithID(rs);
      // createWithID inserts via COLS, which does not include sync_attempt, so it cannot be set on the
      // Sector object above and persisted by this insert - the pin has to be written explicitly afterwards
      // via updateLastSync
      sm.updateLastSync(DSID.of(releaseKey, sectorId), 2);

      // the project pins the OLDEST attempt
      sm.updateLastSync(DSID.of(Datasets.COL, sectorId), 1);

      // the fixture is only meaningful if both pins actually persisted - createWithID silently drops
      // sync_attempt, so assert rather than assume
      assertEquals("project must pin attempt 1", (Integer) 1, sm.get(DSID.of(Datasets.COL, sectorId)).getSyncAttempt());
      assertEquals("release must pin attempt 2", (Integer) 2, sm.get(DSID.of(releaseKey, sectorId)).getSyncAttempt());
    }
  }
}
