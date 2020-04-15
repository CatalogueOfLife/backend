package life.catalogue.assembly;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import io.dropwizard.lifecycle.Managed;
import life.catalogue.api.model.*;
import life.catalogue.common.concurrent.ExecutorUtils;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.importer.ImportManager;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.utils.NamedThreadFactory;
import org.neo4j.helpers.collection.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class AssemblyCoordinator implements Managed {
  static  final Comparator<Sector> SECTOR_ORDER = Comparator.comparing(Sector::getTarget, Comparator.nullsLast(SimpleName::compareTo));
  private static final Logger LOG = LoggerFactory.getLogger(AssemblyCoordinator.class);
  private static final String THREAD_NAME = "assembly-sync";
  
  private ExecutorService exec;
  private ImportManager importManager;
  private final SqlSessionFactory factory;
  private final NameUsageIndexService indexService;
  private final DatasetImportDao diDao;
  private final Map<Integer, SectorFuture> syncs = Collections.synchronizedMap(new LinkedHashMap<Integer, SectorFuture>());
  private final Timer timer;
  private final Map<Integer, AtomicInteger> counter = new HashMap<>();
  private final Map<Integer, AtomicInteger> failed = new HashMap<>();

  static class SectorFuture {
    public final int sectorKey;
    public final int datasetKey;
    public final Future future;
    public final SectorImport state;
    public final boolean delete;
    
    private SectorFuture(SectorRunnable job, Future future) {
      this.sectorKey = job.sectorKey;
      this.datasetKey = job.sector.getDatasetKey();
      this.state = job.getState();
      this.future = future;
      this.delete = job instanceof SectorDelete;
    }

  }
  
  public AssemblyCoordinator(SqlSessionFactory factory, DatasetImportDao diDao, NameUsageIndexService indexService, MetricRegistry registry) {
    this.factory = factory;
    this.diDao = diDao;
    this.indexService = indexService;
    timer = registry.timer("life.catalogue.assembly.timer");
  }
  
  @Override
  public void start() throws Exception {
    LOG.info("Starting assembly coordinator");
    exec = Executors.newSingleThreadExecutor(new NamedThreadFactory(THREAD_NAME, Thread.MAX_PRIORITY, true));
  }
  
  @Override
  public void stop() throws Exception {
    // orderly shutdown running imports
    for (SectorFuture df : syncs.values()) {
      df.future.cancel(true);
    }
    // fully shutdown threadpool within given time
    ExecutorUtils.shutdown(exec, ExecutorUtils.MILLIS_TO_DIE, TimeUnit.MILLISECONDS);
  }
  
  public void setImportManager(ImportManager importManager) {
    this.importManager = importManager;
  }
  
  public AssemblyState getState() {
    return new AssemblyState(syncs.values(), total(failed), total(counter));
  }

  private static int total(Map<Integer, AtomicInteger> cnt) {
    return cnt.values().stream().mapToInt(AtomicInteger::get).sum();
  }

  public AssemblyState getState(int datasetKey) {
    List<SectorFuture> vals = syncs.values().stream()
        .filter(sf -> sf.datasetKey == datasetKey)
        .collect(Collectors.toList());
    return new AssemblyState(vals, valOrZero(failed, datasetKey), valOrZero(counter, datasetKey));
  }

  private static int valOrZero(Map<Integer, AtomicInteger> map, Integer key){
    return map.containsKey(key) ? map.get(key).get() : 0;
  }

  /**
   * Check if any sector from a given dataset is currently syncing and return the sector key. Otherwise null
   */
  public Integer hasSyncingSector(int datasetKey){
    for (SectorFuture df : syncs.values()) {
      if (df.datasetKey == datasetKey) {
        return df.sectorKey;
      }
    }
    return null;
  }
  
  /**
   * Makes sure the dataset has data and is currently not importing
   */
  private void assertStableData(Sector s) throws IllegalArgumentException {
    try (SqlSession session = factory.openSession(true)) {
      try {
        //make sure dataset is currently not imported
        if (importManager != null && importManager.isRunning(s.getSubjectDatasetKey())) {
          LOG.warn("Concurrently running dataset import. Cannot sync {}", s);
          throw new IllegalArgumentException("Dataset "+s.getSubjectDatasetKey()+" currently being imported. Cannot sync " + s);
        }
        NameMapper nm = session.getMapper(NameMapper.class);
        if (nm.hasData(s.getSubjectDatasetKey())) {
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
  
  public void sync(int catalogueKey, RequestScope request, User user) throws IllegalArgumentException {
    if (request.getAll() != null && request.getAll()) {
      syncAll(catalogueKey, user);
    } else {
      if (request.getSectorKey() != null) {
        syncSector(request.getSectorKey(), user);
      }
      if (request.getDatasetKey() != null) {
        LOG.info("Sync all sectors in dataset {}", request.getDatasetKey());
        final AtomicInteger cnt = new AtomicInteger();
        try (SqlSession session = factory.openSession(true)) {
          SectorMapper sm = session.getMapper(SectorMapper.class);
          sm.processSectors(catalogueKey, request.getDatasetKey()).forEach(s -> {
            syncSector(s.getId(), user);
            cnt.getAndIncrement();
          });
        }
        // now that we have them schedule syncs
        LOG.info("Queued {} sectors from dataset {} for sync", cnt.get(), request.getDatasetKey());
      }
    }
  }
  
  private synchronized void syncSector(int sectorKey, User user) throws IllegalArgumentException {
    SectorSync ss = new SectorSync(sectorKey, factory, indexService, diDao, this::successCallBack, this::errorCallBack, user);
    queueJob(ss);
  }

  public void deleteSector(int sectorKey, User user) throws IllegalArgumentException {
    SectorDelete sd = new SectorDelete(sectorKey, factory, indexService, this::successCallBack, this::errorCallBack, user);
    queueJob(sd);
  }
  
  private synchronized void queueJob(SectorRunnable job) throws IllegalArgumentException {
    // is this sector already syncing?
    if (syncs.containsKey(job.sectorKey)) {
      LOG.info("{} already busy", job.sector);
      // ignore
    
    } else {
      assertStableData(job.sector);
      syncs.put(job.sectorKey, new SectorFuture(job, exec.submit(job)));
      LOG.info("Queued {} for {} targeting {}", job.getClass().getSimpleName(), job.sector, job.sector.getTarget());
    }
  }
  
  /**
   * We use old school callbacks here as you cannot easily cancel CompletableFutures.
   */
  private void successCallBack(SectorRunnable sync) {
    syncs.remove(sync.getSectorKey());
    Duration durQueued = Duration.between(sync.getCreated(), sync.getStarted());
    Duration durRun = Duration.between(sync.getStarted(), LocalDateTime.now());
    LOG.info("Sector Sync {} finished. {} min queued, {} min to execute", sync.getSectorKey(), durQueued.toMinutes(), durRun.toMinutes());
    counter.putIfAbsent(sync.catalogueKey, new AtomicInteger(0));
    counter.get(sync.catalogueKey).incrementAndGet();
    timer.update(durRun.getSeconds(), TimeUnit.SECONDS);
  }
  
  /**
   * We use old school callbacks here as you cannot easily cancel CompletableFutures.
   */
  private void errorCallBack(SectorRunnable sync, Exception err) {
    syncs.remove(sync.getSectorKey());
    LOG.error("Sector Sync {} failed: {}", sync.getSectorKey(), err.getCause().getMessage(), err.getCause());
    failed.putIfAbsent(sync.catalogueKey, new AtomicInteger(0));
    failed.get(sync.catalogueKey).incrementAndGet();
  }
  
  public synchronized void cancel(int sectorKey, User user) {
    if (syncs.containsKey(sectorKey)) {
      LOG.info("Sync of sector {} cancelled by user {}", sectorKey, user);
      syncs.remove(sectorKey).future.cancel(true);
    }
  }
  
  private int syncAll(int catalogueKey, User user) {
    LOG.warn("Sync all sectors. Triggered by user {}", user);
    List<Sector> sectors;
    try (SqlSession session = factory.openSession(false)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      sectors = Iterables.asList(sm.processDataset(catalogueKey));
    }
    sectors.sort(SECTOR_ORDER);
    int failed = 0;
    for (Sector s : sectors) {
      try {
        syncSector(s.getId(), user);
      } catch (RuntimeException e) {
        LOG.error("Fail to sync {}: {}", s, e.getMessage());
        failed++;
      }
    }
    int queued = sectors.size()-failed;
    LOG.info("Scheduled {} sectors for sync, {} failed", queued, failed);
    return queued;
  }
  

}
