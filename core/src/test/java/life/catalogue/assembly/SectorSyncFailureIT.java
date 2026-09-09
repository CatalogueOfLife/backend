package life.catalogue.assembly;

import life.catalogue.TestUtils;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SectorImport;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.mapper.SectorImportMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.es.indexing.NameUsageIndexService;
import life.catalogue.junit.NameMatchingRule;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.junit.TreeRepoRule;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * What a sector sync leaves behind when it dies after it has already deleted the sectors previous content.
 *
 * Nothing rolls a sync back - deleteOld() runs on an autocommit session and TreeBaseHandler commits every
 * 1000 usages and once more from close(), the exception path included - so the project keeps whatever the
 * aborted copy managed to write. On 2026-09-01 that cost the COL project 25,712 usages of one ITIS sector
 * and went unnoticed through three release candidates, because sector.sync_attempt still pointed at the
 * last successful attempt and every source metric replayed its counts.
 */
public class SectorSyncFailureIT extends SectorSyncTestBase {

  final static SqlSessionFactoryRule pg = new PgSetupRule();
  final static TestDataRule dataRule = TestDataRule.apple();
  final static TreeRepoRule treeRepoRule = new TreeRepoRule();
  final static NameMatchingRule matchingRule = new NameMatchingRule();
  final static SyncFactoryRule syncFactoryRule = new SyncFactoryRule();

  @ClassRule
  public final static TestRule classRules = RuleChain
    .outerRule(pg)
    .around(dataRule)
    .around(treeRepoRule)
    .around(matchingRule)
    .around(syncFactoryRule);

  /**
   * A SectorRunnable that gets as far as declaring the sectors old content gone and then dies, which is
   * exactly the shape of the incident: deleteOld() had committed, processTree() had not finished.
   */
  static class FailingSync extends SectorRunnable {
    private final boolean destroyFirst;

    FailingSync(DSID<Integer> sectorKey, boolean destroyFirst) throws IllegalArgumentException {
      super(sectorKey, false, SqlSessionFactoryRule.getSqlSessionFactory(), NameUsageIndexService.passThru(),
        syncFactoryRule.getSdao(), syncFactoryRule.getSiDao(), TestUtils.mockedBroker(), null, true,
        TestDataRule.TEST_USER.getKey());
      this.destroyFirst = destroyFirst;
    }

    @Override
    void doWork() {
      if (destroyFirst) {
        markDataDestroyed();
        throw new IllegalStateException("The NameIndexChronicleStore is currently not available");
      }
      throw new IllegalStateException("Sector already queued or running");
    }

    @Override
    void doMetrics() {
      fail("a failed sync must never reach doMetrics");
    }

    @Override
    void updateSearchIndex() {
      fail("a failed sync must never reach updateSearchIndex");
    }
  }

  /**
   * A sync that died after deleteOld() must leave the sector pointing at its own failed attempt: the
   * project no longer holds what the last successful attempt measured, so replaying those counts would be
   * a lie. The failed attempt's row carries no counts at all, which is the honest answer.
   */
  @Test
  public void failedSyncPinsItsOwnAttempt() {
    final var factory = SqlSessionFactoryRule.getSqlSessionFactory();
    final DSID<Integer> sectorKey = createSectorWithGoodSync();
    final int goodAttempt = 1;

    var job = new FailingSync(sectorKey, true);
    final int failedAttempt = job.state.getAttempt();
    assertTrue("the failing sync must take a fresh attempt", failedAttempt > goodAttempt);
    job.run();
    assertEquals(JobStatus.FAILED, job.getStatus());

    try (SqlSession session = factory.openSession(true)) {
      var sm = session.getMapper(SectorMapper.class);
      var sim = session.getMapper(SectorImportMapper.class);

      assertEquals("the sector must point at the attempt that destroyed its content, not at the last good one",
        (Integer) failedAttempt, sm.get(sectorKey).getSyncAttempt());

      var pinned = sim.get(sectorKey, failedAttempt);
      assertNotNull("the failed attempt must still have its metrics row", pinned);
      assertNull("and that row must carry no counts, since doMetrics was never reached", pinned.getNameCount());
      assertNotNull("while the last good attempt is still there to compare against", sim.get(sectorKey, goodAttempt));
    }
  }

  /**
   * A sync that fails before it deletes anything leaves the sector alone: its content still is what the
   * last successful attempt measured, so nothing may move and no release may be blocked. 226 of the 228
   * SectorSync failures on the COL project in 2026 were of this harmless kind.
   */
  @Test
  public void failureBeforeDeleteChangesNothing() {
    final var factory = SqlSessionFactoryRule.getSqlSessionFactory();
    final DSID<Integer> sectorKey = createSectorWithGoodSync();
    final int goodAttempt = 1;

    var job = new FailingSync(sectorKey, false);
    job.run();
    assertEquals(JobStatus.FAILED, job.getStatus());

    try (SqlSession session = factory.openSession(true)) {
      assertEquals("an untouched sector must keep pointing at its last successful attempt",
        (Integer) goodAttempt, session.getMapper(SectorMapper.class).get(sectorKey).getSyncAttempt());
    }
  }

  /**
   * A fresh sector with one successful sync recorded as attempt 1, which the sector pins.
   */
  private DSID<Integer> createSectorWithGoodSync() {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var sm = session.getMapper(SectorMapper.class);
      Sector s = new Sector();
      s.setDatasetKey(Datasets.COL);
      s.setSubjectDatasetKey(TestDataRule.APPLE.key);
      s.setMode(Sector.Mode.ATTACH);
      s.applyUser(Users.TESTER);
      sm.create(s);
      final DSID<Integer> key = DSID.of(Datasets.COL, s.getId());

      SectorImport si = new SectorImport();
      si.setDatasetKey(Datasets.COL);
      si.setSectorKey(s.getId());
      si.setCreatedBy(Users.TESTER);
      si.setNameCount(42);
      session.getMapper(SectorImportMapper.class).create(si);
      assertEquals("fixture: the good sync must be attempt 1", 1, si.getAttempt());
      sm.updateLastSync(key, si.getAttempt());
      assertEquals((Integer) 1, sm.get(key).getSyncAttempt());
      return key;
    }
  }
}
