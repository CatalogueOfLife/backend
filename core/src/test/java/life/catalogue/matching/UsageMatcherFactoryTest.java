package life.catalogue.matching;

import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.event.DatasetDataChanged;
import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Users;
import life.catalogue.config.MatchingConfig;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.metadata.coldp.DatasetJsonWriter;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class UsageMatcherFactoryTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  @Mock SqlSessionFactory sqlSessionFactory;
  @Mock NameIndex nameIndex;
  @Mock JobExecutor executor;

  @Before
  public void clearCache() {
    DatasetInfoCache.CACHE.clear();
  }

  /**
   * Leaves a real, non-empty store on disk for a dataset, as a finished build would. A bare directory is
   * not enough any more: needsRebuild rejects anything that is not a store of the current format.
   */
  private File fakeStore(int key) throws Exception {
    File dir = new File(tmp.getRoot(), String.valueOf(key));
    try (var b = new UsageMatcherFileStoreBuilder(key, dir)) {
      var sn = new SimpleNameCached("u1", "Aus bus", org.gbif.nameparser.api.Rank.SPECIES);
      sn.setCanonicalId(1);
      b.add(sn);
      b.seal().close();
    }
    return dir;
  }

  private UsageMatcherFactory factory() {
    MatchingConfig cfg = new MatchingConfig();
    cfg.storageDir = tmp.getRoot();
    return new UsageMatcherFactory(cfg, nameIndex, sqlSessionFactory, executor);
  }

  /** Wires the shared sqlSessionFactory mock to return the given NameUsageMapper counts for a dataset key. */
  @SuppressWarnings("unchecked")
  private void stubUsageMapper(int key, int count) {
    SqlSession session = mock(SqlSession.class);
    NameUsageMapper num = mock(NameUsageMapper.class);
    DatasetMapper dm = mock(DatasetMapper.class);
    Cursor<SimpleNameCached> cursor = mock(Cursor.class);

    when(sqlSessionFactory.openSession()).thenReturn(session);
    when(session.getMapper(NameUsageMapper.class)).thenReturn(num);
    when(session.getMapper(DatasetMapper.class)).thenReturn(dm);
    when(num.count(key)).thenReturn(count);
    when(num.processDatasetSimpleNidx(key)).thenReturn(cursor);
  }

  private static Dataset dataset(int key, DatasetOrigin origin, boolean privat) {
    return dataset(key, origin, privat, null);
  }

  private static Dataset dataset(int key, DatasetOrigin origin, boolean privat, LocalDateTime deleted) {
    var d = new Dataset();
    d.setKey(key);
    d.setOrigin(origin);
    d.setPrivat(privat);
    d.setDeleted(deleted);
    return d;
  }

  /**
   * Wires the shared sqlSessionFactory so that DatasetMapper.get(key) returns the given dataset (the one
   * datasetDataChanged loads) and the NameUsageMapper counts needed by isSmallDataset / a synchronous build.
   */
  @SuppressWarnings("unchecked")
  private void stubDataChanged(int key, int count, Dataset loaded) {
    SqlSession session = mock(SqlSession.class);
    NameUsageMapper num = mock(NameUsageMapper.class);
    DatasetMapper dm = mock(DatasetMapper.class);
    Cursor<SimpleNameCached> cursor = mock(Cursor.class);

    when(sqlSessionFactory.openSession()).thenReturn(session);
    when(session.getMapper(NameUsageMapper.class)).thenReturn(num);
    when(session.getMapper(DatasetMapper.class)).thenReturn(dm);
    when(num.count(key)).thenReturn(count);
    when(num.processDatasetSimpleNidx(key)).thenReturn(cursor);
    when(dm.get(key)).thenReturn(loaded);
  }

  @Test
  public void publishSchedulesBuildForExternalAboveThreshold() {
    var f = factory();
    stubUsageMapper(100, 5000);          // above default threshold of 100
    Dataset old = dataset(100, DatasetOrigin.EXTERNAL, true);   // was private
    Dataset now = dataset(100, DatasetOrigin.EXTERNAL, false);  // now public
    f.datasetChanged(DatasetChanged.changed(now, old, 1));
    verify(executor).submit(argThat(j -> j instanceof BackgroundJob)); // a build was scheduled
  }

  @Test
  public void publishSkipsSmallDataset() {
    var f = factory();
    stubUsageMapper(101, 5);                 // below threshold
    f.datasetChanged(DatasetChanged.changed(
      dataset(101, DatasetOrigin.EXTERNAL, false), dataset(101, DatasetOrigin.EXTERNAL, true), 1));
    verify(executor, never()).submit(any());
  }

  @Test
  public void publishSkipsProjects() {
    var f = factory();
    f.datasetChanged(DatasetChanged.changed(
      dataset(102, DatasetOrigin.PROJECT, false), dataset(102, DatasetOrigin.PROJECT, true), 1));
    verify(executor, never()).submit(any());
  }

  @Test
  public void maintenanceReconcileRunsAsMatcherBotUser() {
    var f = factory();
    f.maintenance();
    // the startup reconcile must run as the real seeded system user Users.MATCHER (11), NOT the SUPERUSER
    // sentinel (-42), otherwise JobExecutor.submit throws "No user -42 existing" and no reconcile happens.
    verify(executor).submit(argThat(j -> j.getUserKey() == Users.MATCHER));
  }

  @Test
  public void unpublishKeepsMatcherForOnDemandUse() throws Exception {
    var f = factory();
    stubUsageMapper(103, 5000);
    f.persistent(103);                          // create a matcher on disk + cache
    assertNotNull(f.get(103));
    f.datasetChanged(DatasetChanged.changed(
      dataset(103, DatasetOrigin.EXTERNAL, true), dataset(103, DatasetOrigin.EXTERNAL, false), 1));
    // going private no longer destroys the matcher - its editors & reviewers can still match against it
    // and the reconcile TTL reaps it once nobody uses it any more
    assertNotNull(f.get(103));
  }

  @Test
  public void deleteRemovesMatcher() throws Exception {
    var f = factory();
    stubUsageMapper(104, 5000);
    f.persistent(104);
    f.datasetChanged(DatasetChanged.deleted(dataset(104, DatasetOrigin.EXTERNAL, false), 1));
    assertNull(f.get(104));
  }

  @Test
  public void dataChangedSchedulesRebuildForExternalPublishedAboveThreshold() {
    var f = factory();
    stubDataChanged(300, 5000, dataset(300, DatasetOrigin.EXTERNAL, false)); // published, above threshold
    f.datasetDataChanged(new DatasetDataChanged(300, 1));
    verify(executor).submit(argThat(j -> j instanceof BackgroundJob)); // rebuild scheduled
  }

  @Test
  public void dataChangedSkipsProjects() {
    var f = factory();
    stubDataChanged(301, 5000, dataset(301, DatasetOrigin.PROJECT, false)); // projects served live
    f.datasetDataChanged(new DatasetDataChanged(301, 1));
    verify(executor, never()).submit(any());
  }

  @Test
  public void dataChangedSkipsPrivateWithoutStore() {
    var f = factory();
    // private and nobody ever matched against it → no matcher to keep up to date
    stubDataChanged(302, 5000, dataset(302, DatasetOrigin.EXTERNAL, true));
    f.datasetDataChanged(new DatasetDataChanged(302, 1));
    verify(executor, never()).submit(any());
  }

  @Test
  public void dataChangedRebuildsPrivateWithExistingStore() throws Exception {
    var f = factory();
    int key = 305;
    stubDataChanged(key, 5000, dataset(key, DatasetOrigin.EXTERNAL, true));
    f.persistent(key);   // an on demand matcher exists because someone matched against it
    f.datasetDataChanged(new DatasetDataChanged(key, 1));
    // a re-import must refresh it, otherwise it silently serves the previous attempt's data
    verify(executor).submit(argThat(j -> j instanceof BackgroundJob));
  }

  @Test
  public void dataChangedSkipsDeleted() {
    var f = factory();
    stubDataChanged(303, 5000, dataset(303, DatasetOrigin.EXTERNAL, false, LocalDateTime.now())); // deleted
    f.datasetDataChanged(new DatasetDataChanged(303, 1));
    verify(executor, never()).submit(any());
  }

  @Test
  public void dataChangedBelowThresholdRemovesExistingMatcher() throws Exception {
    var f = factory();
    int key = 304;
    stubDataChanged(key, 5, dataset(key, DatasetOrigin.EXTERNAL, false)); // published but below threshold
    f.persistent(key);            // build a matcher on disk + cache
    assertNotNull(f.get(key));
    f.datasetDataChanged(new DatasetDataChanged(key, 1));
    verify(executor, never()).submit(any()); // small → never rebuilt
    assertNull(f.get(key));                   // stale persistent matcher removed
  }

  @Test
  public void reconcileRemovesObsoleteMatcherDir() {
    int staleKey = 1001;
    File staleDir = new File(tmp.getRoot(), String.valueOf(staleKey));
    staleDir.mkdirs();

    // 1001 is no longer a published in-scope dataset (e.g. deleted/unpublished), so searchKeys does not
    // return it → reconcile must delete its obsolete on-disk matcher.
    var f = factory();
    stubReconcile(List.of());

    f.reconcile(false, Users.MATCHER);

    assertFalse("obsolete matcher dir should be removed by reconcile", staleDir.exists());
  }

  @Test
  public void matcherExistsReturnsFalseWhenNeitherInMemoryNorOnDisk() {
    var f = factory();
    assertFalse(f.matcherExists(999));
  }

  @Test
  public void matcherExistsReturnsTrueWhenDirectoryOnDisk() {
    var f = factory();
    new File(tmp.getRoot(), "999").mkdirs();
    assertTrue(f.matcherExists(999));
  }

  @Test
  public void getReturnsNullWhenNoFileOnDisk() throws Exception {
    var f = factory();
    assertNull(f.get(987654)); // nothing on disk, nothing cached
  }

  @Test
  public void openPersistentReturnsSameCachedInstance() throws Exception {
    var f = factory();
    // build a store on disk for key 100 via the synchronous path
    stubUsageMapper(100, /*count*/ 3);
    f.persistent(100);                 // builds + loads + caches
    UsageMatcher a = f.openPersistent(100);
    UsageMatcher b = f.openPersistent(100);
    assertNotNull(a);
    assertSame(a, b);                  // shared cached instance
    assertSame(a, f.get(100));
  }

  @Test
  public void existingOrPostgresThrowsWhileFirstBuildInProgress() {
    var f = factory();
    f.runningBuilds.put(777, 1L); // simulate a first build in progress, nothing cached
    // must fail fast with 503 rather than block on the lock or scan postgres live
    assertThrows(UnavailableException.class, () -> f.existingOrPostgres(777));
  }

  @Test
  public void getReturnsNullWhileBuildInProgressNoCache() throws Exception {
    var f = factory();
    f.runningBuilds.put(778, 1L);
    assertNull("get must not block during a build; null when nothing is cached yet", f.get(778));
  }

  @Test
  public void persistentThrowsWhileBuildInProgress() {
    var f = factory();
    f.runningBuilds.put(776, 1L); // a build owns the key; a second caller must not get null (-> NPE in MatchingJob)
    assertThrows(UnavailableException.class, () -> f.persistent(776));
  }

  @Test
  public void existingOrPostgresSchedulesBuildAndThrowsForLargeDatasetWithoutStore() {
    var f = factory();
    stubUsageMapper(780, 5000);   // above threshold, nothing on disk yet
    // scanning a large dataset live from postgres for every name is what the persistent store exists to
    // avoid, so the first request schedules the build and 503s instead
    assertThrows(UnavailableException.class, () -> f.existingOrPostgres(780));
    verify(executor).submit(argThat(j -> j instanceof BackgroundJob));
  }

  @Test
  public void existingOrPostgresStill503sWhenTheBuildCannotBeScheduled() {
    var f = factory();
    stubUsageMapper(783, 5000);
    // JobExecutor rejects a build that is queued already, which is exactly what a user retrying the same
    // match triggers. That must still read as "not ready yet", not as a server error.
    doThrow(new IllegalArgumentException("An identical job is queued already")).when(executor).submit(any());
    assertThrows(UnavailableException.class, () -> f.existingOrPostgres(783));
  }

  @Test
  public void existingOrPostgresReturnsPostgresMatcherForSmallDataset() throws Exception {
    var f = factory();
    stubUsageMapper(781, 5);         // below threshold → live postgres matching is cheap enough
    UsageMatcher m = f.existingOrPostgres(781);
    try {
      assertNotNull(m);
    } finally {
      m.close();
    }
    verify(executor, never()).submit(any());
  }

  @Test
  public void openPersistentTouchesSidecarAsLastUsedMarker() throws Exception {
    var f = factory();
    int key = 782;
    stubUsageMapper(key, 3);
    f.persistent(key);                                  // builds + caches the store
    // the mocked DatasetMapper returns no dataset, so the build writes no sidecar - do it here instead
    File sidecar = MatchingConfig.datasetJson(new File(tmp.getRoot(), String.valueOf(key)));
    Dataset stored = new Dataset();
    stored.setKey(key);
    stored.setAttempt(1);
    DatasetJsonWriter.write(stored, sidecar);
    long old = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10);
    assertTrue(sidecar.setLastModified(old));

    f.openPersistent(key);                              // a match request against the cached matcher

    // reconcile reads the sidecar mtime as the last-used marker, so serving a match must refresh it
    assertTrue("openPersistent must refresh the sidecar mtime", sidecar.lastModified() > old);
  }

  @Test
  public void existingOrPostgresServesCachedMatcherDuringRebuild() throws Exception {
    var f = factory();
    stubUsageMapper(779, 5000);
    UsageMatcher cached = f.persistent(779);       // build + cache an initial matcher
    assertNotNull(cached);
    f.runningBuilds.put(779, 1L);                   // simulate a rebuild now in progress
    // the old cached matcher is still served (no throw, no block) while the rebuild runs
    assertSame(cached, f.existingOrPostgres(779));
  }

  /**
   * Wires the shared sqlSessionFactory so that DatasetMapper.searchKeys returns the given keys and
   * NameUsageMapper / DatasetMapper are available for isSmallDataset(count) and needsRebuild(get).
   * Returns the two mappers so tests can stub per-key counts and attempts.
   */
  private record ReconcileMocks(DatasetMapper dm, NameUsageMapper num) {}

  private ReconcileMocks stubReconcile(List<Integer> keys) {
    SqlSession session = mock(SqlSession.class);
    DatasetMapper dm = mock(DatasetMapper.class);
    NameUsageMapper num = mock(NameUsageMapper.class);
    when(sqlSessionFactory.openSession()).thenReturn(session);
    when(session.getMapper(DatasetMapper.class)).thenReturn(dm);
    when(session.getMapper(NameUsageMapper.class)).thenReturn(num);
    when(dm.searchKeys(any(), anyInt())).thenReturn(keys);
    return new ReconcileMocks(dm, num);
  }

  @Test
  public void reconcileSchedulesBuildForAboveThresholdMissingStore() {
    var f = factory();
    var m = stubReconcile(List.of(200));
    when(m.num().count(200)).thenReturn(5000);   // above default threshold of 100
    // no store dir on disk and no sidecar → needsRebuild is true

    f.reconcile(false, 1);

    verify(executor).submit(argThat(j -> j instanceof BackgroundJob)); // build scheduled
  }

  @Test
  public void reconcileSkipsBelowThresholdDataset() {
    var f = factory();
    var m = stubReconcile(List.of(201));
    when(m.num().count(201)).thenReturn(5);      // below threshold → small → removed, not built

    f.reconcile(false, 1);

    verify(executor, never()).submit(any());
  }

  @Test
  public void reconcileForceOverridesInSyncSidecar() throws Exception {
    var f = factory();
    int key = 202;
    var m = stubReconcile(List.of(key));
    when(m.num().count(key)).thenReturn(5000);   // above threshold

    // store dir + sidecar present AND the DB attempt equals the sidecar attempt → needsRebuild is FALSE
    File dir = fakeStore(key);
    Dataset stored = new Dataset();
    stored.setKey(key);
    stored.setAttempt(5);
    DatasetJsonWriter.write(stored, MatchingConfig.datasetJson(dir));
    Dataset current = new Dataset();
    current.setKey(key);
    current.setAttempt(5);                        // same attempt as sidecar → in sync
    when(m.dm().get(key)).thenReturn(current);

    // without force the in-sync sidecar means no build is scheduled
    f.reconcile(false, 1);
    verify(executor, never()).submit(any());

    // force overrides the in-sync sidecar and schedules a build for the very same key
    clearInvocations(executor);
    f.reconcile(true, 1);
    verify(executor).submit(argThat(j -> j instanceof BackgroundJob));
  }

  /**
   * A matcher whose attempt sidecar is in sync but whose recorded names-index created timestamp differs
   * from the live index (i.e. the nidx was rebuilt/swapped) is stale and must be rebuilt by reconcile.
   */
  @Test
  public void reconcileSchedulesBuildWhenNidxChanged() throws Exception {
    var f = factory();
    int key = 203;
    var m = stubReconcile(List.of(key));
    when(m.num().count(key)).thenReturn(5000);   // above threshold

    // attempt matches the DB (not stale on attempt), but the sidecar recorded a different nidx than the live one
    inSyncSidecar(key, m, 7, UUID.randomUUID());
    when(nameIndex.id()).thenReturn(UUID.randomUUID()); // a different index → stale

    f.reconcile(false, 1);
    verify(executor).submit(argThat(j -> j instanceof BackgroundJob)); // rebuild scheduled for the stale nidx
  }

  /**
   * A sidecar written before the nidx id existed carries only the timestamp. It must NOT count as stale,
   * exactly as the timestamp marker itself did not when it was introduced - otherwise shipping the id
   * would queue a rebuild of every matcher in one go.
   */
  @Test
  public void reconcileSkipsWhenSidecarHasNoNidxId() throws Exception {
    var f = factory();
    int key = 207;
    var m = stubReconcile(List.of(key));
    when(m.num().count(key)).thenReturn(5000);

    inSyncSidecar(key, m, 7, null); // legacy: nidxCreated only, no nidxId

    f.reconcile(false, 1);
    verify(executor, never()).submit(any());
    // nameIndex.id() is deliberately not stubbed: with nothing recorded there is nothing to compare it
    // to, and mockito's strict stubbing would flag the stub as unused if we added one.
    verify(nameIndex, never()).id();
  }

  /** A matcher in sync on both attempt and names-index created timestamp is not rebuilt. */
  @Test
  public void reconcileSkipsWhenNidxUnchanged() throws Exception {
    var f = factory();
    int key = 204;
    var m = stubReconcile(List.of(key));
    when(m.num().count(key)).thenReturn(5000);

    var nidxId = UUID.randomUUID();
    inSyncSidecar(key, m, 7, nidxId);
    when(nameIndex.id()).thenReturn(nidxId); // same index as recorded → not stale

    f.reconcile(false, 1);
    verify(executor, never()).submit(any());
  }

  /**
   * Creates an on-disk store dir + sidecar for a dataset that is NOT part of the published set, i.e. a matcher
   * that only exists because someone matched against it. {@code lastUsedMillisAgo} backdates the sidecar mtime,
   * which is the last-used marker reconcile ages on demand matchers out by.
   */
  private File onDemandStore(int key, ReconcileMocks m, Dataset current, int storedAttempt,
                             UUID nidxId, long lastUsedMillisAgo) throws Exception {
    File dir = fakeStore(key);
    Dataset stored = new Dataset();
    stored.setKey(key);
    stored.setAttempt(storedAttempt);
    ObjectNode node = ApiModule.MAPPER.valueToTree(stored);
    node.put("nidxCreated", LocalDateTime.of(2026, 1, 1, 0, 0).toString());
    node.put("nidxId", nidxId.toString());
    File sidecar = MatchingConfig.datasetJson(dir);
    ApiModule.MAPPER.writeValue(sidecar, node);
    assertTrue(sidecar.setLastModified(System.currentTimeMillis() - lastUsedMillisAgo));
    when(m.dm().get(key)).thenReturn(current);
    return dir;
  }

  @Test
  public void reconcileKeepsRecentlyUsedOnDemandMatcher() throws Exception {
    var f = factory();
    int key = 205;
    var m = stubReconcile(List.of());   // private → never part of the published set
    var nidx = UUID.randomUUID();
    var current = dataset(key, DatasetOrigin.EXTERNAL, true);
    current.setAttempt(3);
    File dir = onDemandStore(key, m, current, 3, nidx, TimeUnit.DAYS.toMillis(1));
    when(m.num().count(key)).thenReturn(5000);   // above threshold
    when(nameIndex.id()).thenReturn(nidx);

    f.reconcile(false, 1);

    assertTrue("a recently used on demand matcher must survive reconcile", dir.exists());
    verify(executor, never()).submit(any());     // in sync → nothing to rebuild either
  }

  @Test
  public void reconcileRemovesExpiredOnDemandMatcher() throws Exception {
    var f = factory();
    int key = 206;
    var m = stubReconcile(List.of());
    var current = dataset(key, DatasetOrigin.EXTERNAL, true);
    current.setAttempt(3);
    File dir = onDemandStore(key, m, current, 3, UUID.randomUUID(), TimeUnit.DAYS.toMillis(31));

    f.reconcile(false, 1);

    assertFalse("an on demand matcher unused past the TTL must be removed", dir.exists());
  }

  @Test
  public void reconcileRemovesOnDemandMatcherOfDeletedDataset() throws Exception {
    var f = factory();
    int key = 207;
    var m = stubReconcile(List.of());
    var current = dataset(key, DatasetOrigin.EXTERNAL, true, LocalDateTime.now());
    File dir = onDemandStore(key, m, current, 3, UUID.randomUUID(), TimeUnit.DAYS.toMillis(1));

    f.reconcile(false, 1);

    assertFalse("a deleted dataset keeps no matcher, however recently it was used", dir.exists());
  }

  @Test
  public void reconcileRefreshesStaleOnDemandMatcher() throws Exception {
    var f = factory();
    int key = 208;
    var m = stubReconcile(List.of());
    var current = dataset(key, DatasetOrigin.EXTERNAL, true);
    current.setAttempt(9);                       // the dataset was re-imported since the store was built
    onDemandStore(key, m, current, 3, UUID.randomUUID(), TimeUnit.DAYS.toMillis(1));
    when(m.num().count(key)).thenReturn(5000);

    f.reconcile(false, 1);

    verify(executor).submit(argThat(j -> j instanceof BackgroundJob));
  }

  @Test
  public void migratesLegacySidecarIntoStoreDir() throws Exception {
    var f = factory();
    int key = 900;
    File dir = fakeStore(key);
    File legacy = new File(tmp.getRoot(), key + ".json");
    Dataset stored = new Dataset();
    stored.setKey(key);
    stored.setAttempt(7);
    DatasetJsonWriter.write(stored, legacy);
    long lastUsed = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3);
    assertTrue(legacy.setLastModified(lastUsed));

    f.maintenance();

    File moved = MatchingConfig.datasetJson(dir);
    assertTrue("the sidecar must move into the store dir", moved.isFile());
    assertFalse(legacy.exists());
    // the mtime is the last-used marker on demand matchers are aged out by, it must survive the move
    assertEquals(lastUsed / 1000, moved.lastModified() / 1000);
  }

  @Test
  public void maintenanceRemovesOrphanedSidecar() throws Exception {
    var f = factory();
    // no store dir for 901, so this sidecar was leaked by an interrupted removal
    File orphan = new File(tmp.getRoot(), "901.json");
    DatasetJsonWriter.write(dataset(901, DatasetOrigin.EXTERNAL, false), orphan);

    f.maintenance();

    assertFalse(orphan.exists());
  }

  /**
   * A rebuild writes a fresh sidecar, but must not make an unused on demand matcher look freshly used -
   * a regularly reimported private dataset would otherwise keep its matcher forever.
   */
  @Test
  public void rebuildKeepsLastUsedMarker() throws Exception {
    var f = factory();
    int key = 902;
    stubUsageMapper(key, 3);
    f.persistent(key).close();                       // first build
    File sidecar = MatchingConfig.datasetJson(new File(tmp.getRoot(), String.valueOf(key)));
    Dataset stored = new Dataset();
    stored.setKey(key);
    stored.setAttempt(1);
    DatasetJsonWriter.write(stored, sidecar);
    long old = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(20);
    assertTrue(sidecar.setLastModified(old));

    f.rebuild(key, 1);
    var job = org.mockito.ArgumentCaptor.forClass(BackgroundJob.class);
    verify(executor).submit(job.capture());
    job.getValue().run();                            // the rebuild + swap the executor would have run

    assertTrue(sidecar.isFile());
    assertEquals("the rebuild must not reset the last used marker", old / 1000, sidecar.lastModified() / 1000);
  }

  @Test
  public void removeLeavesNothingBehind() throws Exception {
    var f = factory();
    int key = 903;
    File dir = fakeStore(key);
    DatasetJsonWriter.write(dataset(key, DatasetOrigin.EXTERNAL, false), MatchingConfig.datasetJson(dir));

    f.remove(key);

    assertEquals(0, tmp.getRoot().list().length);
  }

  /**
   * Writes a store dir + dataset sidecar for key (attempt + embedded nidxCreated) and wires the DB to report
   * the same attempt (in sync), mirroring what {@code writeSidecar} produces after a build.
   */
  private void inSyncSidecar(int key, ReconcileMocks m, int attempt, UUID nidxId) throws Exception {
    File dir = fakeStore(key);
    Dataset stored = new Dataset();
    stored.setKey(key);
    stored.setAttempt(attempt);
    ObjectNode node = ApiModule.MAPPER.valueToTree(stored);
    node.put("nidxCreated", LocalDateTime.of(2026, 1, 1, 0, 0).toString());
    if (nidxId != null) {
      node.put("nidxId", nidxId.toString());
    }
    ApiModule.MAPPER.writeValue(MatchingConfig.datasetJson(dir), node);
    Dataset current = new Dataset();
    current.setKey(key);
    current.setAttempt(attempt);
    when(m.dm().get(key)).thenReturn(current);
  }
}
