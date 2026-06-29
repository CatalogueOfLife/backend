package life.catalogue.matching;

import com.google.common.base.Preconditions;

import java.util.concurrent.locks.ReentrantLock;
import jakarta.validation.constraints.NotNull;
import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.event.DatasetDataChanged;
import life.catalogue.api.event.DatasetListener;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetSimple;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.*;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.ExecutorUtils;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.concurrent.NamedThreadFactory;
import life.catalogue.config.MatchingConfig;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.metadata.coldp.ColdpMetadataParser;
import life.catalogue.metadata.coldp.DatasetJsonWriter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.fory.Fory;
import org.apache.fory.ThreadLocalFory;
import org.apache.fory.ThreadSafeFory;
import org.apache.fory.config.CompatibleMode;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Factory to create and reuse persistent usage matchers,
 * which are specific for an entire dataset.
 */
public class UsageMatcherFactory implements DatasetListener, life.catalogue.common.Managed {
  private final static Logger LOG = LoggerFactory.getLogger(UsageMatcherFactory.class);
  private volatile boolean started = false;
  private final NameIndex nameIndex;
  private final SqlSessionFactory factory;
  private final MatchingConfig cfg;
  private final File dir;
  private final ConcurrentHashMap<Integer, LocalDateTime> runningBuilds = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Integer, UsageMatcher> matchers = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Integer, ReentrantLock> buildLocks = new ConcurrentHashMap<>();
  private final JobExecutor executor;

  static final ThreadSafeFory FURY = new ThreadLocalFory(classLoader -> {
    Fory fury = Fory.builder()
      .withLanguage(org.apache.fory.config.Language.JAVA)
      .withClassLoader(classLoader)
      .withRefTracking(false)
      .withCompatibleMode(CompatibleMode.SCHEMA_CONSISTENT)
      .requireClassRegistration(true)
      .build();
    return register(fury);
  });
  private static Fory register(Fory fury) {
    fury.register(SimpleNameCached.class);
    fury.register(MatchType.class);
    fury.register(Rank.class);
    fury.register(NomCode.class);
    fury.register(TaxonomicStatus.class);
    fury.register(TaxGroup.class);
    return fury;
  }

  public UsageMatcherFactory(MatchingConfig cfg, NameIndex nameIndex, SqlSessionFactory factory, JobExecutor executor) {
    this.nameIndex = Preconditions.checkNotNull(nameIndex);
    this.executor = executor;
    this.factory = Preconditions.checkNotNull(factory);
    this.dir = cfg.storageDir;
    this.cfg = cfg;
  }

  /**
   * Eagerly opens and validates every persisted matcher under the storage dir, populating the cache.
   * Not called at startup (matchers are opened lazily); kept for the MatcherCmd CLI and admin use.
   */
  public int loadAllFromDisk() {
    LOG.info("Load existing file based matchers from {}", dir);
    var fsKeys = listFS();
    if (fsKeys.isEmpty()) return matchers.size();

    var validKeys = new ArrayList<Integer>();
    for (int key : fsKeys) {
      try {
        DatasetInfoCache.CACHE.info(key);
        validKeys.add(key);
      } catch (NotFoundException e) {
        File f = cfg.dir(key);
        LOG.warn("Dataset {} not existing, delete matching storage folder {}", key, f);
        FileUtils.deleteQuietly(f);
      }
    }
    if (validKeys.isEmpty()) return matchers.size();

    int threads = Math.min(8, validKeys.size());
    var exec = Executors.newFixedThreadPool(threads, new NamedThreadFactory("matcher-loader"));
    var loaded = new ConcurrentHashMap<Integer, UsageMatcher>();
    for (int key : validKeys) {
      final int k = key;
      exec.submit(() -> {
        try {
          var store = reopenStore(k);
          var m = new UsageMatcher(k, nameIndex, store, true);
          if (m.store().isEmpty()) {
            LOG.warn("Matcher for dataset {} is empty, delete storage files {}", k, cfg.dir(k));
            store.close(); // m.close() is a no-op while keepStoreOpen=true, so close the store directly
            FileUtils.deleteQuietly(cfg.dir(k));
          } else {
            loaded.put(k, m);
            LOG.info("Loaded matcher with {} usages and {} canonical ids for dataset {}", store.size(), store.canonicalSize(), k);
          }
        } catch (IOException e) {
          File f = cfg.dir(k);
          LOG.warn("Matcher for dataset {} cannot be loaded. Delete storage files {}", k, f, e);
          FileUtils.deleteQuietly(f);
        }
      });
    }
    ExecutorUtils.shutdown(exec);

    matchers.putAll(loaded);
    LOG.info("Loaded {} matchers from {}", matchers.size(), dir);
    return matchers.size();
  }

  private UsageMatcherChronicleStore reopenStore(int datasetKey) throws IOException {
    return UsageMatcherChronicleStore.reopen(datasetKey, cfg.dir(datasetKey));
  }

  public NameIndex getNameIndex() {
    return nameIndex;
  }

  /**
   * Datasets with fewer usages than this threshold use a Postgres-backed matcher and have no
   * persistent file store. 0 disables the threshold (all datasets get a persistent matcher).
   */
  public int getPgMatcherThreshold() {
    return cfg.pgMatcherThreshold;
  }

  /**
   * Reopens the persisted chronicle store for a dataset from disk and caches it, or returns null
   * if there is no (or an empty/corrupt) store on disk. Concurrent callers share one instance.
   */
  public UsageMatcher openPersistent(int datasetKey) throws IOException {
    UsageMatcher existing = matchers.get(datasetKey);
    if (existing != null) return existing;
    if (dir == null || !cfg.dir(datasetKey).isDirectory()) return null;
    ReentrantLock lock = lockFor(datasetKey);
    lock.lock();
    try {
      existing = matchers.get(datasetKey); // double-check under lock
      if (existing != null) return existing;
      var store = reopenStore(datasetKey);
      var m = new UsageMatcher(datasetKey, nameIndex, store, true);
      if (m.store().isEmpty()) {
        LOG.warn("Matcher for dataset {} is empty, delete storage files {}", datasetKey, cfg.dir(datasetKey));
        store.close(); // m.close() is a no-op while keepStoreOpen=true, so close the store directly
        FileUtils.deleteQuietly(cfg.dir(datasetKey));
        return null;
      }
      matchers.put(datasetKey, m);
      LOG.info("Reopened matcher with {} usages for dataset {}", store.size(), datasetKey);
      return m;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Returns the matcher for a dataset, opening its persisted store from disk on first use.
   * Returns null when no persistent matcher exists on disk.
   */
  public UsageMatcher get(int datasetKey) throws IOException {
    return openPersistent(datasetKey);
  }

  private boolean isSmallDataset(int datasetKey) {
    if (cfg.pgMatcherThreshold <= 0) return false;
    try (SqlSession s = factory.openSession()) {
      return s.getMapper(NameUsageMapper.class).count(datasetKey) < cfg.pgMatcherThreshold;
    }
  }

  /** Asynchronously removes and rebuilds a single matcher. The "rebuild one" lever. */
  public BackgroundJob rebuild(int datasetKey, int userKey) {
    var job = new MatcherBuildJob(datasetKey, userKey);
    executor.submit(job);
    return job;
  }

  private class MatcherBuildJob extends BackgroundJob {
    private final int datasetKey;

    MatcherBuildJob(int datasetKey, int userKey) {
      super(userKey);
      this.datasetKey = datasetKey;
    }

    @Override
    public void execute() throws Exception {
      // remove + rebuild atomically under ONE acquisition of the never-evicted per-dataset lock,
      // so a concurrent openPersistent/get of the SAME dataset cannot race the file deletion+rebuild.
      ReentrantLock lock = lockFor(datasetKey);
      lock.lock();
      try {
        evictLocked(datasetKey);     // close old in-memory matcher + delete stale files, under the lock
        loadFreshLocked(datasetKey); // build fresh + load + sidecar + cache, same lock
      } finally {
        lock.unlock();
      }
    }

    @Override
    public boolean isDuplicate(BackgroundJob other) {
      return other instanceof MatcherBuildJob && ((MatcherBuildJob) other).datasetKey == datasetKey;
    }
  }

  /**
   * Returns the persistent matcher (reopened from disk if needed), or a postgres matcher reading
   * live data when no persistent store exists. All matchers must be closed after use.
   */
  public UsageMatcher existingOrPostgres(int datasetKey) throws IOException {
    UsageMatcher m = openPersistent(datasetKey);
    return m != null ? m : postgres(datasetKey);
  }

  /**
   * Creates a matcher that reads directly from Postgres using the given session.
   * The session lifecycle is entirely the caller's responsibility.
   */
  public UsageMatcher postgres(int datasetKey, SqlSession session) {
    LOG.info("Create new postgres session matcher for dataset {}", datasetKey);
    return new UsageMatcher(datasetKey, nameIndex, new UsageMatcherPgStore(datasetKey, session), false);
  }

  /**
   * Creates a matcher that reads directly from Postgres, opening a fresh session per operation.
   * No connection is held between calls.
   */
  public UsageMatcher postgres(int datasetKey) {
    LOG.info("Create new postgres factory matcher for dataset {}", datasetKey);
    return new UsageMatcher(datasetKey, nameIndex, new UsageMatcherPgStore(datasetKey, factory), false);
  }

  private ReentrantLock lockFor(int datasetKey) {
    return buildLocks.computeIfAbsent(datasetKey, k -> new ReentrantLock());
  }

  /** Creates a fresh, EMPTY, correctly-sized persistent chronicle store, replacing any existing files. */
  private UsageMatcher createEmptyPersistent(int datasetKey) throws IOException {
    FileUtils.deleteQuietly(cfg.dir(datasetKey)); // clear stale files first
    try (SqlSession s = factory.openSession()) {
      var num = s.getMapper(NameUsageMapper.class);
      int count = num.count(datasetKey);
      var samples = num.listSN(datasetKey, new Page(0, 5));
      int canon = num.countDistinctCanonical(datasetKey);
      long canonCount = canon + Math.max(1, canon / 100);
      return buildPersistentMatcher(datasetKey, samples, count + 1, canonCount, cfg, nameIndex);
    }
  }

  /** Writes the dataset sidecar JSON (attempt marker) next to the matcher store. */
  private void writeSidecar(int datasetKey) {
    try (var s = factory.openSession()) {
      Dataset d = s.getMapper(DatasetMapper.class).get(datasetKey);
      if (d != null) {
        DatasetJsonWriter.write(d, cfg.datasetJson(datasetKey));
        LOG.info("Wrote dataset sidecar for matcher {} with attempt {}", datasetKey, d.getAttempt());
      }
    } catch (Exception e) {
      LOG.warn("Failed to write sidecar for matcher {}", datasetKey, e);
    }
  }

  /**
   * Builds a fresh persistent matcher and loads it from the DB synchronously, caching it.
   * Caller MUST already hold lockFor(datasetKey).
   */
  private UsageMatcher loadFreshLocked(int datasetKey) throws IOException {
    if (runningBuilds.containsKey(datasetKey)) {
      throw new IllegalStateException("Matcher for dataset " + datasetKey + " is already being built. Please try again in a few minutes");
    }
    runningBuilds.put(datasetKey, LocalDateTime.now());
    try {
      UsageMatcher m = createEmptyPersistent(datasetKey);
      m.store().load(factory);
      writeSidecar(datasetKey);
      matchers.put(datasetKey, m);
      return m;
    } finally {
      runningBuilds.remove(datasetKey);
    }
  }

  /** Get-or-build entry: returns the cached matcher or builds+loads it synchronously under the per-dataset lock. */
  private UsageMatcher buildAndLoad(int datasetKey) throws IOException {
    ReentrantLock lock = lockFor(datasetKey);
    lock.lock();
    try {
      UsageMatcher existing = matchers.get(datasetKey); // double-check under lock
      if (existing != null) return existing;
      return loadFreshLocked(datasetKey);
    } finally {
      lock.unlock();
    }
  }

  /** Reopens the persistent matcher from disk, or builds+loads it synchronously if absent. */
  public UsageMatcher persistent(int datasetKey) throws IOException {
    UsageMatcher m = openPersistent(datasetKey);
    return m != null ? m : buildAndLoad(datasetKey);
  }

  public static UsageMatcher buildPersistentMatcher(int datasetKey, List<SimpleNameCached> samples, int maxUsages, long canonCount, MatchingConfig cfg, NameIndex nameIndex) throws IOException {
    var f = cfg.dir(datasetKey);
    LOG.info("Create new persistent chronicle matcher for dataset {} at {}", datasetKey, f);
    var store = UsageMatcherChronicleStore.build(datasetKey, f, maxUsages, canonCount, samples);
    return new UsageMatcher(datasetKey, nameIndex, store, true);
  }

  public UsageMatcher memory(int datasetKey) {
    LOG.info("Create new in memory matcher for dataset {}", datasetKey);
    var store = new UsageMatcherMemStore(datasetKey);
    return new UsageMatcher(datasetKey, nameIndex, store, false);
  }

  // package-private for testing
  boolean matcherExists(int datasetKey) {
    return matchers.containsKey(datasetKey) || (cfg.storageDir != null && cfg.dir(datasetKey).exists());
  }

  private Integer readStoredAttempt(int datasetKey) {
    var sidecar = cfg.datasetJson(datasetKey);
    if (!sidecar.exists()) return null;
    try {
      var opt = ColdpMetadataParser.readJSON(new FileInputStream(sidecar));
      return opt.map(dws -> dws.getDataset().getAttempt()).orElse(null);
    } catch (Exception e) {
      LOG.warn("Could not read sidecar for dataset {}", datasetKey, e);
      return null;
    }
  }

  @Override
  public void datasetChanged(DatasetChanged d) {
    if (d.isDeletion()) {
      remove(d.key);
    } else if (d.isUpdated() && d.obj != null && d.old != null) {
      boolean wasPublished = !d.old.isPrivat();
      boolean isPublished = !d.obj.isPrivat();
      if (!wasPublished && isPublished) {
        ensurePublishedMatcher(d.obj, d.user);
      } else if (wasPublished && !isPublished) {
        remove(d.key); // unpublished → drop matcher + sidecar
      }
    }
  }

  /** Schedules a build for a newly published dataset if it is in scope and above threshold. */
  private void ensurePublishedMatcher(Dataset d, int userKey) {
    if (dir == null) return;
    var origin = d.getOrigin();
    if (origin != DatasetOrigin.EXTERNAL && !origin.isRelease()) return; // projects served live
    try {
      if (!isSmallDataset(d.getKey())) {
        rebuild(d.getKey(), userKey);
      }
    } catch (Exception e) {
      LOG.error("Failed to create matcher for newly published dataset {}", d.getKey(), e);
    }
  }

  @Override
  public void datasetDataChanged(DatasetDataChanged event) {
    if (dir == null) return;
    Dataset d;
    try (SqlSession s = factory.openSession()) {
      d = s.getMapper(DatasetMapper.class).get(event.datasetKey);
    }
    if (d == null || d.getDeleted() != null || d.isPrivat()) return; // only published, non-deleted
    var origin = d.getOrigin();
    if (origin != DatasetOrigin.EXTERNAL && !origin.isRelease()) return;
    try {
      if (isSmallDataset(event.datasetKey)) {
        remove(event.datasetKey); // drop any stale persistent file; served live from postgres now
      } else {
        rebuild(event.datasetKey, event.user); // refresh to new attempt; creates if missing
      }
    } catch (Exception e) {
      LOG.error("Failed to refresh matcher for dataset {}", event.datasetKey, e);
    }
  }

  /**
   * Enforces the matcher invariant for all published, non-deleted EXTERNAL/RELEASE/XRELEASE datasets.
   * force=false: build only missing/stale matchers (startup). force=true: rebuild all (the "rebuild all" lever).
   */
  public void reconcile(boolean force, int userKey) {
    if (dir == null) return;
    List<Integer> keys;
    try (SqlSession s = factory.openSession()) {
      var req = new DatasetSearchRequest();
      req.setPrivat(false);
      req.setInclDeleted(false);
      req.setOrigin(List.of(DatasetOrigin.EXTERNAL, DatasetOrigin.RELEASE, DatasetOrigin.XRELEASE));
      keys = s.getMapper(DatasetMapper.class).searchKeys(req, Users.SUPERUSER);
    }
    int scheduled = 0;
    for (int key : keys) {
      try {
        if (isSmallDataset(key)) {
          remove(key); // below threshold → no persistent file
          continue;
        }
        if (force || needsRebuild(key)) {
          rebuild(key, userKey);
          scheduled++;
        }
      } catch (Exception e) {
        LOG.error("Failed to reconcile matcher for dataset {}", key, e);
      }
    }
    LOG.info("Reconcile scheduled {} matcher builds (force={}) of {} published datasets", scheduled, force, keys.size());
  }

  /** True when no store exists on disk or the stored sidecar attempt differs from the current DB attempt. */
  private boolean needsRebuild(int datasetKey) {
    if (!cfg.dir(datasetKey).isDirectory()) return true;
    Integer stored = readStoredAttempt(datasetKey);
    Integer current;
    try (SqlSession s = factory.openSession()) {
      Dataset d = s.getMapper(DatasetMapper.class).get(datasetKey);
      current = d == null ? null : d.getAttempt();
    }
    return stored == null || !stored.equals(current);
  }

  public void remove(int datasetKey) {
    ReentrantLock lock = lockFor(datasetKey);
    lock.lock();
    try {
      evictLocked(datasetKey);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Closes & removes the cached matcher and deletes its on-disk store + sidecar.
   * Caller MUST already hold lockFor(datasetKey). The per-dataset lock is intentionally never evicted
   * (a bounded ReentrantLock per dataset is a negligible leak) so lazy-open, removal and rebuild stay
   * mutually exclusive.
   */
  private void evictLocked(int datasetKey) {
    if (matchers.containsKey(datasetKey)) {
      LOG.info("Delete matcher for dataset {}", datasetKey);
      var m = matchers.remove(datasetKey);
      if (m != null) {
        try {
          m.store().close();
        } catch (Exception e) {
          LOG.error("Failed to close matcher for dataset {}", datasetKey, e);
        }
      }
    }
    if (dir != null) {
      FileUtils.deleteQuietly(cfg.dir(datasetKey));
      FileUtils.deleteQuietly(cfg.datasetJson(datasetKey));
    }
  }

  public static class FactoryMetadata {
    public final int count;
    public final List<MatcherMetadata> matchers;

    public FactoryMetadata(List<MatcherMetadata> matchers) {
      this.matchers = matchers;
      this.count = matchers.size();
    }
  }

  public static class MatcherMetadata implements Comparable<MatcherMetadata> {
    @Override
    public int compareTo(@NotNull MatcherMetadata o) {
      return Integer.compare(datasetKey, o.datasetKey);
    }

    public final int datasetKey;
    public final Integer size;
    public final Integer attempt; // from sidecar JSON, null if absent
    public DatasetSimple dataset;

    public MatcherMetadata(int datasetKey, Integer size, Integer attempt) {
      this.datasetKey = datasetKey;
      this.size = size;
      this.attempt = attempt;
    }
  }

  public MatcherMetadata metadata(int datasetKey) {
    if (matchers.containsKey(datasetKey)) {
      var m = matchers.get(datasetKey);
      return new MatcherMetadata(datasetKey, m.store().size(), readStoredAttempt(datasetKey));
    }
    return null;
  }

  public FactoryMetadata metadata(boolean decorate) {
    List<MatcherMetadata> matchers = new ArrayList<>();
    for (var e : this.matchers.entrySet()) {
      matchers.add(new MatcherMetadata(e.getKey(), e.getValue().store().size(), readStoredAttempt(e.getKey())));
    }
    // decorate with dataset metadata
    if (decorate) {
      try (SqlSession session = factory.openSession()) {
        var dm = session.getMapper(DatasetMapper.class);
        for (MatcherMetadata m : matchers) {
          m.dataset = dm.getSimple(m.datasetKey);
        }
      }
    }
    // sort by datasetKey
    Collections.sort(matchers);
    return new FactoryMetadata(matchers);
  }

  private List<Integer> listFS() {
    List<Integer> keys = new ArrayList<>();
    if (dir != null && dir.isDirectory()) {
      String[] files = dir.list(DirectoryFileFilter.INSTANCE);
      if (files != null) {
        for (var fn : files) {
          try {
            keys.add(Integer.parseInt(fn));
          } catch (NumberFormatException e) {
            // ignore non-dataset entries
          }
        }
      }
    }
    return keys;
  }

  @Override
  public void start() {
    started = true;
    if (dir != null) {
      executor.submit(new ReconcileJob(Users.SUPERUSER));
    }
  }

  @Override
  public void stop() {
    close();
    started = false;
  }

  @Override
  public boolean hasStarted() {
    return started;
  }

  private class ReconcileJob extends BackgroundJob {
    ReconcileJob(int userKey) { super(userKey); }
    @Override public void execute() { reconcile(false, getUserKey()); }
    @Override public boolean isDuplicate(BackgroundJob other) { return other instanceof ReconcileJob; }
  }

  public void close() {
    for (UsageMatcher m : matchers.values()) {
      try {
        m.store().close();
      } catch (Exception e) {
        LOG.error("Failed to close matcher for dataset {}", m.datasetKey, e);
      }
    }
    matchers.clear();
  }
}
