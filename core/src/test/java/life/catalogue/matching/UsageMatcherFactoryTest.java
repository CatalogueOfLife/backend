package life.catalogue.matching;

import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.event.DatasetDataChanged;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetSimple;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Users;
import life.catalogue.config.MatchingConfig;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.metadata.coldp.DatasetJsonWriter;

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
import java.util.List;

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

  private UsageMatcherFactory factory() {
    MatchingConfig cfg = new MatchingConfig();
    cfg.storageDir = tmp.getRoot();
    return new UsageMatcherFactory(cfg, nameIndex, sqlSessionFactory, executor);
  }

  private UsageMatcherFactory factoryWithDatasets(DatasetSimple... datasets) {
    DatasetInfoCache.CACHE.setFactory(sqlSessionFactory);
    SqlSession session = mock(SqlSession.class);
    DatasetMapper dm = mock(DatasetMapper.class);
    when(sqlSessionFactory.openSession()).thenReturn(session);
    when(session.getMapper(DatasetMapper.class)).thenReturn(dm);

    MatchingConfig cfg = new MatchingConfig();
    cfg.storageDir = tmp.getRoot();
    return new UsageMatcherFactory(cfg, nameIndex, sqlSessionFactory, executor);
  }

  /** Wires the shared sqlSessionFactory mock to return the given NameUsageMapper counts for a dataset key. */
  @SuppressWarnings("unchecked")
  private void stubUsageMapper(int key, int count, int canon) {
    SqlSession session = mock(SqlSession.class);
    NameUsageMapper num = mock(NameUsageMapper.class);
    DatasetMapper dm = mock(DatasetMapper.class);
    Cursor<SimpleNameCached> cursor = mock(Cursor.class);

    when(sqlSessionFactory.openSession()).thenReturn(session);
    when(session.getMapper(NameUsageMapper.class)).thenReturn(num);
    when(session.getMapper(DatasetMapper.class)).thenReturn(dm);
    when(num.count(key)).thenReturn(count);
    when(num.listSN(eq(key), any())).thenReturn(List.of());
    when(num.countDistinctCanonical(key)).thenReturn(canon);
    when(num.processDatasetSimpleNidx(key)).thenReturn(cursor);
  }

  private static DatasetSimple simpleDataset(int key, DatasetOrigin origin, boolean deleted) {
    var d = new DatasetSimple();
    d.setKey(key);
    d.setOrigin(origin);
    d.setDeleted(deleted);
    return d;
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
    when(num.listSN(eq(key), any())).thenReturn(List.of());
    when(num.countDistinctCanonical(key)).thenReturn(Math.max(1, count));
    when(num.processDatasetSimpleNidx(key)).thenReturn(cursor);
    when(dm.get(key)).thenReturn(loaded);
  }

  @Test
  public void publishSchedulesBuildForExternalAboveThreshold() {
    var f = factory();
    stubUsageMapper(100, 5000, 4000);          // above default threshold of 100
    Dataset old = dataset(100, DatasetOrigin.EXTERNAL, true);   // was private
    Dataset now = dataset(100, DatasetOrigin.EXTERNAL, false);  // now public
    f.datasetChanged(DatasetChanged.changed(now, old, 1));
    verify(executor).submit(argThat(j -> j instanceof BackgroundJob)); // a build was scheduled
  }

  @Test
  public void publishSkipsSmallDataset() {
    var f = factory();
    stubUsageMapper(101, 5, 5);                 // below threshold
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
  public void startReconcileRunsAsMatcherBotUser() {
    var f = factory();
    f.start();
    // the startup reconcile must run as the real seeded system user Users.MATCHER (11), NOT the SUPERUSER
    // sentinel (-42), otherwise JobExecutor.submit throws "No user -42 existing" and no reconcile happens.
    verify(executor).submit(argThat(j -> j.getUserKey() == Users.MATCHER));
    assertTrue(f.hasStarted());
  }

  @Test
  public void unpublishRemovesMatcher() throws Exception {
    var f = factory();
    stubUsageMapper(103, 5000, 4000);
    f.persistent(103);                          // create a matcher on disk + cache
    assertNotNull(f.get(103));
    f.datasetChanged(DatasetChanged.changed(
      dataset(103, DatasetOrigin.EXTERNAL, true), dataset(103, DatasetOrigin.EXTERNAL, false), 1));
    assertNull(f.get(103));                      // removed
  }

  @Test
  public void deleteRemovesMatcher() throws Exception {
    var f = factory();
    stubUsageMapper(104, 5000, 4000);
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
  public void dataChangedSkipsPrivate() {
    var f = factory();
    stubDataChanged(302, 5000, dataset(302, DatasetOrigin.EXTERNAL, true)); // private → not served
    f.datasetDataChanged(new DatasetDataChanged(302, 1));
    verify(executor, never()).submit(any());
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
  public void loadFromFSDeletesStaleMatcherDirs() {
    int staleKey = 1001;
    File staleDir = new File(tmp.getRoot(), String.valueOf(staleKey));
    staleDir.mkdirs();

    // dataset 1001 is deleted in the DB — warm cache marks it deleted, info() throws NotFoundException
    // The constructor no longer preloads; call loadAllFromDisk() explicitly to validate on-disk state.
    factoryWithDatasets(simpleDataset(staleKey, DatasetOrigin.EXTERNAL, true))
      .loadAllFromDisk();

    assertFalse("stale matcher dir should be deleted on startup", staleDir.exists());
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
    stubUsageMapper(100, /*count*/ 3, /*canon*/ 2);
    f.persistent(100);                 // builds + loads + caches
    UsageMatcher a = f.openPersistent(100);
    UsageMatcher b = f.openPersistent(100);
    assertNotNull(a);
    assertSame(a, b);                  // shared cached instance
    assertSame(a, f.get(100));
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
    new File(tmp.getRoot(), String.valueOf(key)).mkdirs();
    Dataset stored = new Dataset();
    stored.setKey(key);
    stored.setAttempt(5);
    DatasetJsonWriter.write(stored, new File(tmp.getRoot(), key + ".json"));
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
}
