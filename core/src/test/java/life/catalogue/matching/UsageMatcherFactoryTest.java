package life.catalogue.matching;

import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetSimple;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.vocab.DatasetOrigin;
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
    var d = new Dataset();
    d.setKey(key);
    d.setOrigin(origin);
    d.setPrivat(privat);
    return d;
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
  public void cleanupRemovesStaleMatcher() throws Exception {
    var f = factory();
    int key = 42;

    // create a fake matcher dir and sidecar with attempt=5
    File matcherDir = new File(tmp.getRoot(), String.valueOf(key));
    matcherDir.mkdirs();
    Dataset stored = new Dataset();
    stored.setKey(key);
    stored.setAttempt(5);
    DatasetJsonWriter.write(stored, new File(tmp.getRoot(), key + ".json"));

    // mock DB returning attempt=7
    Dataset current = new Dataset();
    current.setKey(key);
    current.setAttempt(7);
    SqlSession session = mock(SqlSession.class);
    DatasetMapper dm = mock(DatasetMapper.class);
    when(sqlSessionFactory.openSession()).thenReturn(session);
    when(session.getMapper(DatasetMapper.class)).thenReturn(dm);
    when(dm.get(key)).thenReturn(current);

    int removed = f.cleanup();

    assertEquals(1, removed);
    assertFalse("matcher dir should be deleted", matcherDir.exists());
    assertFalse("sidecar should be deleted", new File(tmp.getRoot(), key + ".json").exists());
  }

  @Test
  public void cleanupKeepsFreshMatcher() throws Exception {
    var f = factory();
    int key = 43;

    File matcherDir = new File(tmp.getRoot(), String.valueOf(key));
    matcherDir.mkdirs();
    Dataset stored = new Dataset();
    stored.setKey(key);
    stored.setAttempt(9);
    DatasetJsonWriter.write(stored, new File(tmp.getRoot(), key + ".json"));

    Dataset current = new Dataset();
    current.setKey(key);
    current.setAttempt(9);
    SqlSession session = mock(SqlSession.class);
    DatasetMapper dm = mock(DatasetMapper.class);
    when(sqlSessionFactory.openSession()).thenReturn(session);
    when(session.getMapper(DatasetMapper.class)).thenReturn(dm);
    when(dm.get(key)).thenReturn(current);

    int removed = f.cleanup();

    assertEquals(0, removed);
    assertTrue("matcher dir should remain", matcherDir.exists());
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
}
