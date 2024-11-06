package life.catalogue.assembly;

import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.event.DeleteSector;
import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.Setting;
import life.catalogue.common.Idle;
import life.catalogue.common.Managed;
import life.catalogue.concurrent.ExecutorUtils;
import life.catalogue.config.SyncManagerConfig;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.SectorImportMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.importer.ImportManager;
import life.catalogue.matching.nidx.NameIndex;

import org.gbif.nameparser.utils.NamedThreadFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.eventbus.Subscribe;

public class SyncManager implements Managed, Idle {
  static  final Comparator<Sector> SECTOR_ORDER = Comparator.comparing(Sector::getTarget, Comparator.nullsLast(SimpleName::compareTo));
  private static final Logger LOG = LoggerFactory.getLogger(SyncManager.class);
  private static final String THREAD_NAME = "assembly-sync";
  private static final String SCHEDULER_THREAD_NAME = "sync-scheduler";

  private ExecutorService exec;
  private ImportManager importManager;
  private final SyncManagerConfig cfg;
  private Thread schedulerThread;
  private SyncSchedulerJob schedulerJob;
  private final NameIndex nameIndex;
  private final SqlSessionFactory factory;
  private final SyncFactory syncFactory;
  private final Map<DSID<Integer>, SectorFuture> syncs = Collections.synchronizedMap(new LinkedHashMap<>());
  private final Timer timer;
  private final Map<Integer, AtomicInteger> counter = new HashMap<>(); // by dataset (project) key
  private final Map<Integer, AtomicInteger> failed = new HashMap<>();  // by dataset (project) key

  static class SectorFuture {
    public final DSID<Integer> sectorKey;
    public final Future<?> future;
    public final SectorImport state;
    public final boolean delete;
    
    private SectorFuture(SectorRunnable job, Future<?> future) {
      this.sectorKey = DSID.copy(job.sectorKey);
      this.state = job.getState();
      this.future = future;
      this.delete = job instanceof SectorDelete || job instanceof SectorDeleteFull;
    }
  }
  
  public SyncManager(SyncManagerConfig cfg, SqlSessionFactory factory, NameIndex nameIndex, SyncFactory syncFactory, MetricRegistry registry) {
    this.cfg = cfg;
    this.factory = factory;
    this.syncFactory = syncFactory;
    this.nameIndex = nameIndex;
    timer = registry.timer("life.catalogue.assembly.timer");
  }
  
  @Override
  public void start() throws Exception {
    LOG.info("Starting assembly coordinator");
    exec = Executors.newSingleThreadExecutor(new NamedThreadFactory(THREAD_NAME, Thread.MAX_PRIORITY, true));

    // cancel all existing syncs/deletions
    try (SqlSession session = factory.openSession(true)) {
      SectorImportMapper sim = session.getMapper(SectorImportMapper.class);
      // list all imports with running states & waiting
      Page page = new Page(0, Page.MAX_LIMIT);
      List<SectorImport> sims = null;
      while (page.getOffset() == 0 || (sims != null && sims.size() == page.getLimit())) {
        sims = sim.list(null, null, null, ImportState.runningAndWaitingStates(), null, page);
        for (SectorImport si : sims) {
          si.setState(ImportState.CANCELED);
          si.setFinished(LocalDateTime.now());
          sim.update(si);
        }
        page.next();
      }
    }

    // scheduler
    if (cfg.polling > 0) {
      LOG.info("Enable sync scheduler");
      schedulerJob = new SyncSchedulerJob(cfg, this, factory);
      schedulerThread = new Thread(schedulerJob, SCHEDULER_THREAD_NAME);
      LOG.info("Start sync scheduler with polling every {} minutes", cfg.polling);
      schedulerThread.start();

    } else {
      LOG.warn("Sync scheduler disabled");
    }
  }

  @Override
  public void stop() throws Exception {
    // scheduler
    if (schedulerJob != null) {
      schedulerJob.terminate();
    }
    if (schedulerThread != null) {
      LOG.info("Stop sync scheduler");
      schedulerThread.join(ExecutorUtils.MILLIS_TO_DIE);
    }
    // manager
    if (exec != null) {
      LOG.info("Stop assembly coordinator");
      // orderly shutdown running syncs
      for (SectorFuture df : syncs.values()) {
        df.future.cancel(true);
      }
      // fully shutdown threadpool within given time
      ExecutorUtils.shutdown(exec, ExecutorUtils.MILLIS_TO_DIE, TimeUnit.MILLISECONDS);
      exec = null;
    }
  }

  @Override
  public boolean hasStarted() {
    return exec != null;
  }

  @Override
  public boolean isIdle() {
    return !hasStarted() || getState().isIdle();
  }

  public void setImportManager(ImportManager importManager) {
    this.importManager = importManager;
  }
  
  public SyncState getState() {
    return new SyncState(syncs.values(), total(failed), total(counter));
  }

  private static int total(Map<Integer, AtomicInteger> cnt) {
    return cnt.values().stream().mapToInt(AtomicInteger::get).sum();
  }

  public SyncState getState(int datasetKey) {
    List<SectorFuture> vals = syncs.values().stream()
        .filter(sf -> sf.sectorKey.getDatasetKey() == datasetKey)
        .collect(Collectors.toList());
    return new SyncState(vals, valOrZero(failed, datasetKey), valOrZero(counter, datasetKey));
  }

  private static int valOrZero(Map<Integer, AtomicInteger> map, Integer key){
    return map.containsKey(key) ? map.get(key).get() : 0;
  }

  /**
   * Check if any sector from a given dataset is currently syncing and return the sector key. Otherwise null
   */
  public DSID<Integer> hasSyncingSector(int datasetKey){
    for (SectorFuture df : syncs.values()) {
      if (df.sectorKey.getDatasetKey() == datasetKey) {
        return df.sectorKey;
      }
    }
    return null;
  }

  /**
   * Makes sure the dataset has data and is currently not importing
   */
  private void assertStableData(SectorRunnable job) throws IllegalArgumentException {
    Sector s = job.sector;
    try (SqlSession session = factory.openSession(true)) {
      try {
        //make sure dataset is currently not imported
        if (importManager != null && importManager.isRunning(s.getSubjectDatasetKey())) {
          LOG.warn("Concurrently running dataset import. Cannot sync {}", s);
          throw new IllegalArgumentException("Dataset "+s.getSubjectDatasetKey()+" currently being imported. Cannot sync " + s);
        }
        NameMapper nm = session.getMapper(NameMapper.class);
        if (!(job instanceof SectorSync) || nm.hasData(s.getSubjectDatasetKey())) {
          return;
        }
      } catch (PersistenceException e) {
        // missing partitions cause this
        LOG.debug("No partition exists for dataset {}", s.getSubjectDatasetKey(), e);
      }
    }
    LOG.warn("Cannot sync {} which has never been imported", s);
    throw new IllegalArgumentException("Dataset empty. Cannot sync " + s);
  }

  private DatasetSettings projectSettings(int projectKey) {
    try (SqlSession session = factory.openSession(true)) {
      return session.getMapper(DatasetMapper.class).getSettings(projectKey);
    }
  }

  void sync(Sector sector, int user) throws IllegalArgumentException {
    DatasetInfoCache.CACHE.info(sector.getDatasetKey()).requireOrigin(DatasetOrigin.PROJECT);
    RequestScope req = new RequestScope();
    req.setSectorKey(sector.getId());
    sync(sector.getDatasetKey(), req, user);
  }
  public void sync(int projectKey, RequestScope request, int user) throws IllegalArgumentException {
    nameIndex.assertOnline();
    var settings = projectSettings(projectKey);
    final boolean blockMergeSyncs = settings.getBoolDefault(Setting.BLOCK_MERGE_SYNCS, false);
    if (request.getSectorKey() != null) {
      syncSector(DSID.of(projectKey, request.getSectorKey()), user, blockMergeSyncs);
    } else if (request.getDatasetKey() != null) {
      LOG.info("Sync all sectors in source dataset {}", request.getDatasetKey());
      final AtomicInteger cnt = new AtomicInteger();
      try (SqlSession session = factory.openSession(true)) {
        SectorMapper sm = session.getMapper(SectorMapper.class);
        PgUtils.consume(
          () -> sm.processSectors(projectKey, request.getDatasetKey()),
          s -> {
            if (syncSector(s, user, blockMergeSyncs)) {
              cnt.getAndIncrement();
            }
          }
        );
      }
      // now that we have them schedule syncs
      LOG.info("Queued {} sectors from dataset {} for sync", cnt.get(), request.getDatasetKey());
    } else if (request.getAll()) {
      syncAll(projectKey, user, blockMergeSyncs);
    } else {
      throw new IllegalArgumentException("No sectorKey or datasetKey given in request");
    }
  }


  /**
   * @return true if it was actually queued
   * @throws IllegalArgumentException
   */
  private synchronized boolean syncSector(DSID<Integer> sectorKey, int user, boolean blockMergeSyncs) throws IllegalArgumentException {
    SectorSync ss = syncFactory.project(sectorKey, this::successCallBack, this::errorCallBack, user);
    return queueJob(ss, blockMergeSyncs);
  }

  /**
   * @param full if true does a full deletion. Otherwise higher rank taxa are kept unlinked from the sector
   * @return true if the deletion was actually scheduled
   */
  public boolean deleteSector(DSID<Integer> sectorKey, boolean full, int user) throws IllegalArgumentException {
    nameIndex.assertOnline();
    SectorRunnable sd;
    if (full) {
      sd = syncFactory.deleteFull(sectorKey, this::successCallBack, this::errorCallBack, user);
    } else {
      sd = syncFactory.delete(sectorKey, this::successCallBack, this::errorCallBack, user);
    }
    return queueJob(sd, false);
  }

  /**
   * Tries to queue a sync job.
   * If it cannot be queued it updates the import metrics state.
   *
   * @return true if it was actually queued
   * @throws IllegalArgumentException
   * @throws UnavailableException if sync manager or names index are not started
   */
  private synchronized boolean queueJob(SectorRunnable job, boolean blockMergeSyncs) throws IllegalArgumentException {
    try {
      nameIndex.assertOnline();
      this.assertOnline();
      // is this sector already syncing?
      if (syncs.containsKey(job.sectorKey)) {
        // ignore
        return rejectJob(job, String.format("%s already queued or running", job.sector));

      } else if (blockMergeSyncs && Sector.Mode.MERGE == job.sector.getMode()){
        // block merge sector syncs
        return rejectJob(job, String.format("Merge sectors blocked in project, skip sync of sector %s", job.sector));

      } else {
        assertStableData(job);
        syncs.put(job.sectorKey, new SectorFuture(job, exec.submit(job)));
        LOG.info("Queued {} for {} targeting {}", job.getClass().getSimpleName(), job.sector, job.sector.getTarget());
        return true;
      }

    } catch (RuntimeException e) {
      rejectJob(job, e.getMessage());
      throw e;
    }
  }

  private boolean rejectJob(SectorRunnable job, String reason) {
    LOG.info(reason);
    try (SqlSession session = factory.openSession(true)) {
      job.state.setState(ImportState.FAILED);
      job.state.setFinished(LocalDateTime.now());
      session.getMapper(SectorImportMapper.class).update(job.state);
    }
    return false;
  }
  
  /**
   * We use old school callbacks here as you cannot easily cancel CompletableFutures.
   */
  private void successCallBack(SectorRunnable sync) {
    syncs.remove(sync.getSectorKey());
    Duration durQueued = Duration.between(sync.getCreated(), sync.getStarted());
    Duration durRun = Duration.between(sync.getStarted(), LocalDateTime.now());
    LOG.info("Sector Sync {} finished. {} min queued, {} min to execute", sync.getSectorKey(), durQueued.toMinutes(), durRun.toMinutes());
    counter.putIfAbsent(sync.sectorKey.getDatasetKey(), new AtomicInteger(0));
    counter.get(sync.sectorKey.getDatasetKey()).incrementAndGet();
    timer.update(durRun.getSeconds(), TimeUnit.SECONDS);
  }
  
  /**
   * We use old school callbacks here as you cannot easily cancel CompletableFutures.
   */
  private void errorCallBack(SectorRunnable sync, Exception err) {
    syncs.remove(sync.getSectorKey());
    LOG.error("Sector Sync {} failed: {}", sync.getSectorKey(), err.getCause().getMessage(), err.getCause());
    failed.putIfAbsent(sync.sectorKey.getDatasetKey(), new AtomicInteger(0));
    failed.get(sync.sectorKey.getDatasetKey()).incrementAndGet();
  }

  public synchronized void cancel(DSID<Integer> sectorKey, int user) {
    if (syncs.containsKey(sectorKey)) {
      LOG.info("Sync of sector {} cancelled by user {}", sectorKey, user);
      var sync = syncs.remove(sectorKey);
      sync.future.cancel(true);
    }
  }

  private int syncAll(int projectKey, int user, boolean blockMergeSyncs) {
    LOG.warn("Sync all sectors. Triggered by user {}", user);
    final List<Sector> sectors = new ArrayList<>();
    try (SqlSession session = factory.openSession(false)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      PgUtils.consume(()->sm.processDataset(projectKey), sectors::add);
    }
    sectors.sort(SECTOR_ORDER);
    int failed = 0;
    for (Sector s : sectors) {
      try {
        syncSector(s, user, blockMergeSyncs);
      } catch (RuntimeException e) {
        LOG.error("Fail to sync {}: {}", s, e.getMessage());
        failed++;
      }
    }
    int queued = sectors.size()-failed;
    LOG.info("Scheduled {} sectors for sync, {} failed", queued, failed);
    return queued;
  }

  @Subscribe
  public void deleteSectorListener(DeleteSector event){
    LOG.info("Trigger deletion of sector {} by user={}", event.key, event.user);
    var del = deleteSector(event.key, true, event.user);
    if (!del) {
      LOG.warn("Unable to queue deletion of sector {} by user={}", event.key, event.user);
    }
  }

  @Subscribe
  public void datasetDeletedListener(DatasetChanged event){
    if (event.isDeletion()) {
      var keys = syncs.keySet().stream()
                      .filter(k -> k.getDatasetKey().equals(event.key))
                      .collect(Collectors.toSet());
      if (!keys.isEmpty()) {
        LOG.info("Cancel {} sector syncs from deleted dataset {}. User={}", keys.size(), event.key, event.user);
        for (var key : keys) {
          cancel(key, event.user);
        }
      }
    }
  }

}
