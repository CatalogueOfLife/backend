package life.catalogue.matching;

import com.google.common.base.Preconditions;

import java.util.concurrent.locks.ReentrantLock;
import jakarta.validation.constraints.NotNull;
import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.event.DatasetDataChanged;
import life.catalogue.api.event.DatasetListener;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetSimple;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.*;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.config.MatchingConfig;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

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
  private final AtomicLong buildCounter = new AtomicLong();
  // datasetKey -> unique token of the build currently owning it (package-private so tests can simulate a build)
  final ConcurrentHashMap<Integer, Long> runningBuilds = new ConcurrentHashMap<>();
  // package-private so tests can inspect/evict cached instances
  final ConcurrentHashMap<Integer, UsageMatcher> matchers = new ConcurrentHashMap<>();
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
    // tryAcquire fails only if it was retired+closed between the lookup and the acquire → fall through to re-resolve
    if (existing != null && existing.tryAcquire()) return existing;
    // No final store on disk → nothing to reopen. A (re)build writes into a separate temp dir, so during a
    // rebuild the previous (stale) files still live in the final dir and are reopened & served below — only
    // during a true first build is there no final dir, and we return null (existingOrPostgres then 503s).
    // The per-dataset lock is held only for the brief swap, never the long load, so this never blocks long.
    if (dir == null || !cfg.dir(datasetKey).isDirectory()) return null;
    ReentrantLock lock = lockFor(datasetKey);
    lock.lock();
    try {
      existing = matchers.get(datasetKey); // double-check under lock
      if (existing != null && existing.tryAcquire()) return existing;
      var store = reopenStore(datasetKey);
      var m = new UsageMatcher(datasetKey, nameIndex, store, true);
      if (m.store().isEmpty()) {
        LOG.warn("Matcher for dataset {} is empty, delete storage files {}", datasetKey, cfg.dir(datasetKey));
        store.close(); // brand-new instance, never handed out → close the store directly
        FileUtils.deleteQuietly(cfg.dir(datasetKey));
        return null;
      }
      matchers.put(datasetKey, m);
      m.tryAcquire(); // lease for the returning caller (brand-new, never retired → always succeeds)
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
      // build into a temp dir off-lock so the previous matcher keeps serving, then swap atomically.
      buildAndSwap(datasetKey);
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
    if (m != null) return m;
    // a persistent matcher is being built for the first time and is not ready yet. Fail fast with a 503
    // instead of blocking a request thread or scanning the (large) dataset live from postgres.
    if (runningBuilds.containsKey(datasetKey)) {
      throw new UnavailableException("matcher for dataset " + datasetKey + " is being built, please retry shortly");
    }
    return postgres(datasetKey);
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

  /**
   * Builds a fresh persistent matcher in a temporary dir and loads it from the DB, WITHOUT holding the
   * per-dataset lock. The existing matcher (if any) keeps serving while this runs.
   */
  private UsageMatcher buildIntoTemp(int datasetKey, long token) throws IOException {
    File tmpDir = cfg.buildDir(datasetKey, token); // unique per build, so two builds never share a temp dir
    FileUtils.deleteQuietly(tmpDir);
    UsageMatcher m;
    try (SqlSession s = factory.openSession()) {
      var num = s.getMapper(NameUsageMapper.class);
      int count = num.count(datasetKey);
      var samples = num.listSN(datasetKey, new Page(0, 5));
      int canon = num.countDistinctCanonical(datasetKey);
      long canonCount = canon + Math.max(1, canon / 100);
      var store = UsageMatcherChronicleStore.build(datasetKey, tmpDir, count + 1, canonCount, samples);
      m = new UsageMatcher(datasetKey, nameIndex, store, true);
    }
    m.store().load(factory); // the long full-scan load; no lock held
    return m;
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
   * Atomically swaps a freshly built matcher into place under the per-dataset lock: moves the temp files to
   * the final dir and replaces the cached instance. The lock is held only for this quick swap, never for the
   * long build/load. The previous instance is retired (its store closes once its last consumer releases it).
   * Returns the installed matcher, or null if a concurrent remove() cancelled the build.
   */
  private UsageMatcher swapIn(int datasetKey, long token, UsageMatcher fresh) throws IOException {
    ReentrantLock lock = lockFor(datasetKey);
    lock.lock();
    try {
      // only the build that still owns the runningBuilds marker may install its result. A concurrent
      // remove()/delete (which clears the marker) or a superseding build (different token) cancels us.
      Long owner = runningBuilds.get(datasetKey);
      if (owner == null || owner.longValue() != token) {
        LOG.info("Matcher build {} for dataset {} was cancelled/superseded, discarding", token, datasetKey);
        fresh.store().close(); // never handed out, close its store directly
        FileUtils.deleteQuietly(cfg.buildDir(datasetKey, token));
        return null;
      }
      File finalDir = cfg.dir(datasetKey);
      File backup = cfg.backupDir(datasetKey, token);
      FileUtils.deleteQuietly(backup);
      boolean hadFinal = finalDir.isDirectory();
      if (hadFinal) {
        FileUtils.moveDirectory(finalDir, backup); // park the old files; the old store keeps them via its mmap
      }
      try {
        FileUtils.moveDirectory(cfg.buildDir(datasetKey, token), finalDir);
      } catch (IOException e) {
        if (hadFinal) { // restore the previous store so we never destroy the only good copy on a failed move
          FileUtils.deleteQuietly(finalDir);
          try {
            FileUtils.moveDirectory(backup, finalDir);
          } catch (IOException re) {
            LOG.error("Failed to restore previous matcher for dataset {} after a failed swap", datasetKey, re);
          }
        }
        fresh.store().close();
        FileUtils.deleteQuietly(cfg.buildDir(datasetKey, token));
        throw e;
      }
      writeSidecar(datasetKey);
      UsageMatcher old = matchers.put(datasetKey, fresh);
      if (old != null) {
        old.retire(); // closes its store once the last in-flight consumer (e.g. a long MatchingJob) releases it
      }
      FileUtils.deleteQuietly(backup); // old files survive in the old store's mmap until it is actually closed
      LOG.info("Swapped in (re)built matcher with {} usages for dataset {}", fresh.store().size(), datasetKey);
      return fresh;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Builds + loads a matcher off-lock, then atomically swaps it in. Claims the build with a unique token so a
   * concurrent build of the same dataset is skipped. Returns the installed (or already-cached) matcher, or
   * null if the build was cancelled/superseded.
   */
  private UsageMatcher buildAndSwap(int datasetKey) throws IOException {
    long token = buildCounter.incrementAndGet();
    if (runningBuilds.putIfAbsent(datasetKey, token) != null) {
      LOG.info("Matcher for dataset {} is already being built, not starting another", datasetKey);
      return matchers.get(datasetKey); // the old matcher during a rebuild, or null during a first build
    }
    try {
      UsageMatcher fresh = buildIntoTemp(datasetKey, token);
      return swapIn(datasetKey, token, fresh);
    } finally {
      runningBuilds.remove(datasetKey, token); // only clear our own marker, never a superseding build's
    }
  }

  /**
   * Reopens the persistent matcher from disk, or builds it off-lock and swaps it in if absent.
   * Throws {@link UnavailableException} rather than returning null when a build is in progress.
   */
  public UsageMatcher persistent(int datasetKey) throws IOException {
    UsageMatcher m = openPersistent(datasetKey); // acquired for the caller
    if (m != null) return m;
    if (buildAndSwap(datasetKey) == null) {
      throw new UnavailableException("matcher for dataset " + datasetKey + " is being built, please retry shortly");
    }
    // the build installed (or found) a matcher in the cache; acquire it via the normal path
    m = openPersistent(datasetKey);
    if (m == null) {
      throw new UnavailableException("matcher for dataset " + datasetKey + " is being built, please retry shortly");
    }
    return m;
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
   * Removes any on-disk persistent matcher that should no longer exist — below the pgMatcherThreshold,
   * unpublished, deleted, or no longer in scope — and (re)builds the ones that are missing or stale.
   * force=false: build only missing/stale matchers (startup). force=true: rebuild all (the "rebuild all" lever).
   */
  public void reconcile(boolean force, int userKey) {
    if (dir == null) return;
    // datasets that SHOULD have a persistent matcher: published, not deleted, in-scope, above threshold
    List<Integer> published;
    try (SqlSession s = factory.openSession()) {
      var req = new DatasetSearchRequest();
      req.setPrivat(false);
      req.setInclDeleted(false);
      req.setOrigin(List.of(DatasetOrigin.EXTERNAL, DatasetOrigin.RELEASE, DatasetOrigin.XRELEASE));
      published = s.getMapper(DatasetMapper.class).searchKeys(req, Users.SUPERUSER);
    }
    var shouldHave = new HashSet<Integer>();
    for (int key : published) {
      try {
        if (!isSmallDataset(key)) {
          shouldHave.add(key);
        }
      } catch (Exception e) {
        LOG.error("Failed to size dataset {} during reconcile", key, e);
      }
    }
    // remove any on-disk matcher that should no longer exist: below threshold, unpublished, deleted, or out of scope
    int removed = 0;
    for (int key : listFS()) {
      if (!shouldHave.contains(key)) {
        LOG.info("Reconcile: removing obsolete persistent matcher for dataset {}", key);
        remove(key);
        removed++;
      }
    }
    // (re)build the matchers that are missing or stale
    int scheduled = 0;
    for (int key : shouldHave) {
      try {
        if (force || needsRebuild(key)) {
          rebuild(key, userKey);
          scheduled++;
        }
      } catch (Exception e) {
        LOG.error("Failed to reconcile matcher for dataset {}", key, e);
      }
    }
    LOG.info("Reconcile removed {} obsolete and scheduled {} (re)builds (force={}); {} of {} published datasets in scope",
      removed, scheduled, force, shouldHave.size(), published.size());
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
    runningBuilds.remove(datasetKey); // cancel any in-flight build so its swap is discarded
    UsageMatcher m = matchers.remove(datasetKey);
    if (m != null) {
      LOG.info("Delete matcher for dataset {}", datasetKey);
      m.retire(); // closes its store once the last in-flight consumer releases it
    }
    if (dir != null) {
      FileUtils.deleteQuietly(cfg.dir(datasetKey));
      FileUtils.deleteQuietly(cfg.datasetJson(datasetKey));
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
    var m = matchers.get(datasetKey);
    if (m != null && m.tryAcquire()) { // hold a lease so the store can't be closed while we read its size
      try {
        return new MatcherMetadata(datasetKey, m.store().size(), readStoredAttempt(datasetKey));
      } finally {
        m.close();
      }
    }
    return null;
  }

  /** Removes stray {@code .building}/{@code .old} dirs left behind by a crashed or interrupted swap. */
  private void cleanupTempDirs() {
    if (dir == null || !dir.isDirectory()) return;
    String[] names = dir.list();
    if (names == null) return;
    for (String n : names) {
      if (MatchingConfig.isTransientDir(n)) {
        LOG.info("Removing stray matcher temp dir {}", n);
        FileUtils.deleteQuietly(new File(dir, n));
      }
    }
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
      cleanupTempDirs(); // remove crash-leftover .building/.old dirs at startup, before any builds run
      executor.submit(new ReconcileJob(Users.MATCHER));
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
