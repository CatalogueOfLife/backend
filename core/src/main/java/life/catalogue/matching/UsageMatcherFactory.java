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
public class UsageMatcherFactory implements DatasetListener, AutoCloseable {
  private final static Logger LOG = LoggerFactory.getLogger(UsageMatcherFactory.class);
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

  /**
   * @return true if a matcher for the given datasetKey already exists, false otherwise.
   */
  public boolean exists(int datasetKey) throws IOException {
    return matchers.containsKey(datasetKey);
  }

  /**
   * Depending on the dataset origin either a persistent or postgres matcher is created.
   * Persistent matchers are reused and should be closed after use.
   * @param datasetKey
   * @return
   */
  public UsageMatcher build(int datasetKey) throws IOException {
    var info = DatasetInfoCache.CACHE.info(datasetKey);
    if (info.origin == DatasetOrigin.PROJECT) {
      return postgres(datasetKey);
    } else if (dir != null) {
      int count;
      try (SqlSession s = factory.openSession()) {
        count = s.getMapper(NameUsageMapper.class).count(datasetKey);
      }
      if (cfg.pgMatcherThreshold > 0 && count < cfg.pgMatcherThreshold) {
        return postgres(datasetKey);
      }
      return persistent(datasetKey);
    } else {
      return memory(datasetKey);
    }
  }

  private boolean isSmallDataset(int datasetKey) {
    if (cfg.pgMatcherThreshold <= 0) return false;
    try (SqlSession s = factory.openSession()) {
      return s.getMapper(NameUsageMapper.class).count(datasetKey) < cfg.pgMatcherThreshold;
    }
  }

  /**
   * Prepares a persistent matcher for the given datasetKey if it does not yet exist.
   *
   * Loading matchers can take a while so this method prepares them async
   * @param datasetKey
   * @return true if a matcher is being prepared, false if it already existed.
   * @throws IllegalArgumentException if the datasetKey is a project
   */
  public BackgroundJob prepare(int datasetKey, int userKey) throws IOException {
    var m = matchers.get(datasetKey);
    if (m != null && !m.store().isEmpty()) {
      return null;
    } else if (m != null && m.store().isEmpty()) {
      LOG.warn("Rebuild empty existing matcher for dataset {}", datasetKey);
      remove(datasetKey);
    }
    if (dir == null) {
      throw new IllegalStateException("Cannot prepare persistent matcher for dataset " + datasetKey + " because no storage dir is configured");
    }
    var info = DatasetInfoCache.CACHE.info(datasetKey);
    if (info.origin == DatasetOrigin.PROJECT) {
      throw new IllegalArgumentException("Cannot prepare persistent matcher for projects");
    }
    var newMatcher = persistent(datasetKey);
    var job = new AsyncMatchLoader(newMatcher, userKey);
    executor.submit(job);
    return job;
  }

  public void build(DatasetSearchRequest req) {
    req.setInclDeleted(false);
    try (SqlSession session = factory.openSession()) {
      var dm = session.getMapper(DatasetMapper.class);
      var datasets = dm.searchKeys(req, Users.SUPERUSER);
      for (var dk : datasets) {
        if (!matcherExists(dk)) {
          prepare(dk, Users.SUPERUSER);
        }
      }
    } catch (Exception e) {
      LOG.error("Failed to build matchers for request {}", req, e);
    }
  }

  public void rebuild(DatasetSearchRequest req, int userKey) {
    req.setInclDeleted(false);
    try (SqlSession session = factory.openSession()) {
      var dm = session.getMapper(DatasetMapper.class);
      var datasets = dm.searchKeys(req, userKey);
      for (var dk : datasets) {
        remove(dk);
        if (!isSmallDataset(dk)) {
          prepare(dk, userKey);
        }
      }
    } catch (Exception e) {
      LOG.error("Failed to rebuild matchers for request {}", req, e);
    }
  }

  public void rebuildExisting(int userKey) {
    for (var m : matchers.keySet()) {
      try {
        remove(m);
        prepare(m, userKey);
      } catch (IOException e) {
        LOG.error("Failed to rebuild matcher {}", m, e);
      }
    }
  }

  private class AsyncMatchLoader extends BackgroundJob {
    private final UsageMatcher matcher;

    public AsyncMatchLoader(UsageMatcher matcher, int userKey) {
      super(userKey);
      this.matcher = matcher;
    }

    @Override
    public void execute() throws Exception {
      if (runningBuilds.contains(matcher.datasetKey)) {
        throw new IllegalStateException("Matcher for dataset " + matcher.datasetKey + " is already being built.");
      }
      try {
        runningBuilds.put(matcher.datasetKey, LocalDateTime.now());
        matcher.store().load(factory);
        try (var s = factory.openSession()) {
          Dataset d = s.getMapper(DatasetMapper.class).get(matcher.datasetKey);
          if (d != null) {
            DatasetJsonWriter.write(d, UsageMatcherFactory.this.cfg.datasetJson(matcher.datasetKey));
            LOG.info("Wrote dataset sidecar for matcher {} with attempt {}", matcher.datasetKey, d.getAttempt());
          }
        } catch (Exception e) {
          LOG.warn("Failed to write sidecar for matcher {}", matcher.datasetKey, e);
        }
      } finally {
        matcher.close();
        var start = runningBuilds.remove(matcher.datasetKey);
        LOG.info("Matcher for dataset {} loaded in {} seconds", matcher.datasetKey, LocalDateTime.now().minusSeconds(start.getSecond()).getSecond());
      }
    }

    @Override
    public boolean isDuplicate(BackgroundJob other) {
      if (other instanceof AsyncMatchLoader) {
        return matcher.datasetKey == ((AsyncMatchLoader) other).matcher.datasetKey;
      }
      return super.isDuplicate(other);
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

  /** Builds a fresh persistent matcher and loads it from the DB synchronously, caching it. */
  private UsageMatcher buildAndLoad(int datasetKey) throws IOException {
    ReentrantLock lock = lockFor(datasetKey);
    lock.lock();
    try {
      UsageMatcher existing = matchers.get(datasetKey);
      if (existing != null) return existing;
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
    }
  }

  @Override
  public void datasetDataChanged(DatasetDataChanged event) {
    var info = DatasetInfoCache.CACHE.info(event.datasetKey);
    if (info.origin == DatasetOrigin.PROJECT || info.origin.isRelease()) {
      return;
    }
    if (!matcherExists(event.datasetKey)) {
      return;
    }
    try {
      remove(event.datasetKey);
      if (!isSmallDataset(event.datasetKey)) {
        prepare(event.datasetKey, event.user);
      }
    } catch (Exception e) {
      LOG.error("Failed to recreate persistent matcher for dataset {}", event.datasetKey, e);
    }
  }

  public void removeAll() {
    for (var m : matchers.keySet()) {
      remove(m);
    }
  }

  public void remove(DatasetSearchRequest req) {
    try (SqlSession session = factory.openSession()) {
      var dm = session.getMapper(DatasetMapper.class);
      var datasets = dm.searchKeys(req, Users.SUPERUSER);
      for (var dk : datasets) {
        remove(dk);
      }
    }
  }
  public void remove(int datasetKey) {
    buildLocks.remove(datasetKey);
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

  public int reload() {
    close();
    return loadAllFromDisk();
  }

  /**
   * Removes matchers whose stored Dataset attempt (from the sidecar JSON) no longer matches
   * the current attempt in the database. Does not trigger rebuilds — callers get a fresh
   * matcher lazily on next use.
   *
   * @return number of stale matchers removed
   */
  public int cleanup() {
    int removed = 0;
    try (SqlSession session = factory.openSession()) {
      var dm = session.getMapper(DatasetMapper.class);
      for (int key : listFS()) {
        var sidecar = cfg.datasetJson(key);
        if (!sidecar.exists()) continue;
        try {
          var opt = ColdpMetadataParser.readJSON(new FileInputStream(sidecar));
          if (opt.isEmpty()) continue;
          Integer stored = opt.get().getDataset().getAttempt();
          Dataset current = dm.get(key);
          if (stored != null && current != null && !stored.equals(current.getAttempt())) {
            LOG.info("Removing stale matcher for dataset {}: stored attempt {} != current {}",
              key, stored, current.getAttempt());
            remove(key);
            removed++;
          }
        } catch (Exception e) {
          LOG.warn("Could not validate sidecar for dataset {}, skipping", key, e);
        }
      }
    }
    return removed;
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
