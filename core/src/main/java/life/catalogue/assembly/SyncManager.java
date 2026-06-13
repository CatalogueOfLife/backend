package life.catalogue.assembly;

import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.event.DatasetListener;
import life.catalogue.api.event.DeleteSector;
import life.catalogue.api.event.SectorListener;
import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Setting;
import life.catalogue.common.Idle;
import life.catalogue.common.Managed;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.concurrent.ExecutorUtils;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.dao.JobDao;
import life.catalogue.config.SyncManagerConfig;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.SectorImportMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.matching.nidx.NameIndex;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;

/**
 * Coordinates sector syncs and deletions, which are executed as background jobs
 * on the SYNC lane of the shared JobExecutor.
 * Jobs of the same project are serialized by the executor, syncs of different projects run in parallel.
 */
public class SyncManager implements Managed, Idle, SectorListener, DatasetListener {
  static  final Comparator<Sector> SECTOR_ORDER = Comparator.comparing(Sector::getTarget, Comparator.nullsLast(SimpleName::compareTo));
  private static final Logger LOG = LoggerFactory.getLogger(SyncManager.class);
  private static final String SCHEDULER_THREAD_NAME = "sync-scheduler";

  private boolean started;
  private final SyncManagerConfig cfg;
  private Thread schedulerThread;
  private SyncSchedulerJob schedulerJob;
  private final NameIndex nameIndex;
  private final SqlSessionFactory factory;
  private final SyncFactory syncFactory;
  private final JobExecutor executor;
  private final @Nullable JobDao jobDao;
  private final SyncCounter counter;

  public SyncManager(SyncManagerConfig cfg, SqlSessionFactory factory, NameIndex nameIndex, SyncFactory syncFactory,
                     JobExecutor executor, @Nullable JobDao jobDao, MetricRegistry registry) {
    this.cfg = cfg;
    this.factory = factory;
    this.syncFactory = syncFactory;
    this.nameIndex = nameIndex;
    this.executor = executor;
    this.jobDao = jobDao;
    this.counter = new SyncCounter(registry.timer("life.catalogue.assembly.timer"));
  }

  @Override
  public void start() throws Exception {
    LOG.info("Starting assembly coordinator");
    started = true;
    // sector imports left in running states by a previous server are covered by
    // the job executor cancelling all stale job records on startup

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
    // running and queued syncs live in the shared job executor which interrupts them on its own shutdown
    started = false;
  }

  @Override
  public boolean hasStarted() {
    return started;
  }

  @Override
  public boolean isIdle() {
    return !hasStarted() || getState().isIdle();
  }

  /**
   * @return all sector jobs of the executor, both queued and running
   */
  private List<SectorRunnable> syncJobs() {
    return executor.getQueueByJobClass(SectorRunnable.class);
  }

  public SyncState getState() {
    return new SyncState(syncJobs(), counter.failedTotal(), counter.completedTotal());
  }

  public SyncState getState(int datasetKey) {
    var jobs = syncJobs().stream()
        .filter(job -> job.getSectorKey().getDatasetKey() == datasetKey)
        .collect(Collectors.toList());
    return new SyncState(jobs, counter.getFailed(datasetKey), counter.getCompleted(datasetKey));
  }

  /**
   * Check if any sector job currently running or queued involves the given dataset,
   * either as the project being synced into or as the subject source dataset, and return its sector key. Otherwise null
   */
  public DSID<Integer> hasSyncingSector(int datasetKey){
    for (SectorRunnable job : syncJobs()) {
      if (job.getSectorKey().getDatasetKey() == datasetKey || job.subjectDatasetKey == datasetKey) {
        return job.getSectorKey();
      }
    }
    return null;
  }

  /**
   * Makes sure the dataset has data
   */
  private void assertDataExists(SectorRunnable job) throws IllegalArgumentException {
    Sector s = job.sector;
    try (SqlSession session = factory.openSession(true)) {
      NameMapper nm = session.getMapper(NameMapper.class);
      if (job instanceof SectorSync && !nm.hasData(s.getSubjectDatasetKey())) {
        LOG.warn("Cannot sync {} which has no data and probably never has been imported", s);
        throw new IllegalArgumentException("Dataset empty. Cannot sync " + s);
      }
    }
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
    Sector.Mode mode;
    try (SqlSession session = factory.openSession(true)) {
      mode = session.getMapper(SectorMapper.class).getMode(sectorKey.getDatasetKey(), sectorKey.getId());
    }
    if (mode == null) {
      throw new IllegalArgumentException("Sector " + sectorKey + " does not exist");
    }
    SectorRunnable sr = mode == Sector.Mode.HIERARCHY
      ? syncFactory.hierarchy(sectorKey, counter, user)
      : syncFactory.project(sectorKey, counter, user);
    return queueJob(sr, blockMergeSyncs);
  }

  /**
   * @param full if true does a full deletion. Otherwise higher rank taxa are kept unlinked from the sector
   * @return true if the deletion was actually scheduled
   */
  public boolean deleteSector(DSID<Integer> sectorKey, boolean full, int user) throws IllegalArgumentException {
    nameIndex.assertOnline();
    SectorRunnable sd;
    if (full) {
      sd = syncFactory.deleteFull(sectorKey, counter, user);
    } else {
      sd = syncFactory.delete(sectorKey, counter, user);
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
      if (isQueuedOrRunning(job.getSectorKey())) {
        // ignore
        return rejectJob(job, String.format("%s already queued or running", job.sector));

      } else if (blockMergeSyncs && Sector.Mode.MERGE == job.sector.getMode()){
        // block merge sector syncs
        return rejectJob(job, String.format("Merge sectors blocked in project, skip sync of sector %s", job.sector));

      } else {
        assertDataExists(job);
        executor.submit(job);
        LOG.info("Queued {} for {} targeting {}", job.getClass().getSimpleName(), job.sector, job.sector.getTarget());
        return true;
      }

    } catch (RuntimeException e) {
      rejectJob(job, e.getMessage());
      throw e;
    }
  }

  private boolean isQueuedOrRunning(DSID<Integer> sectorKey) {
    return syncJobs().stream().anyMatch(job -> job.getSectorKey().equals(sectorKey));
  }

  private boolean rejectJob(SectorRunnable job, String reason) {
    LOG.warn(reason);
    // record a failed job so the already created sector import attempt links to a final status
    job.setStatus(JobStatus.FAILED);
    job.setError(new IllegalArgumentException(reason));
    if (jobDao != null) {
      jobDao.create(job);
    }
    job.state.setFinished(LocalDateTime.now());
    job.state.setError(reason);
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(SectorImportMapper.class).update(job.state);
    }
    return false;
  }

  public synchronized void cancel(DSID<Integer> sectorKey, int user) {
    for (SectorRunnable job : syncJobs()) {
      if (job.getSectorKey().equals(sectorKey)) {
        LOG.info("Sync of sector {} cancelled by user {}", sectorKey, user);
        executor.cancel(job.getKey(), user);
      }
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

  @Override
  public void sectorDeleted(DeleteSector event){
    LOG.info("Trigger deletion of sector {} by user={}", event.key, event.user);
    var del = deleteSector(event.key, true, event.user);
    if (!del) {
      LOG.warn("Unable to queue deletion of sector {} by user={}", event.key, event.user);
    }
  }

  @Override
  public void datasetChanged(DatasetChanged event){
    if (event.isDeletion()) {
      var keys = syncJobs().stream()
                      .map(SectorRunnable::getSectorKey)
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
