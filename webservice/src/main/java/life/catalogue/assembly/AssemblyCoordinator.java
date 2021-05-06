package life.catalogue.assembly;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import io.dropwizard.lifecycle.Managed;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.concurrent.ExecutorUtils;
import life.catalogue.dao.SectorImportDao;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.SectorImportMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.importer.ImportManager;
import life.catalogue.matching.NameIndex;
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
  private final NameIndex nameIndex;
  private final SqlSessionFactory factory;
  private final NameUsageIndexService indexService;
  private final SectorImportDao sid;
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
  
  public AssemblyCoordinator(SqlSessionFactory factory, NameIndex nameIndex, SectorImportDao sid, NameUsageIndexService indexService, MetricRegistry registry) {
    this.factory = factory;
    this.sid = sid;
    this.indexService = indexService;
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
      Set<ImportState> states = new HashSet<>(ImportState.runningStates());
      states.add(ImportState.WAITING);
      Page page = new Page(0, Page.MAX_LIMIT);
      List<SectorImport> sims = null;
      while (sims == null || sims.size() == page.getLimit()) {
        sims = sim.list(null, null, null, ImportState.runningStates(), null, page);
        for (SectorImport si : sims) {
          si.setState(ImportState.CANCELED);
          si.setFinished(LocalDateTime.now());
          sim.update(si);
        }
        page.next();
      }
    }
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
        .filter(sf -> sf.sectorKey.getDatasetKey() == datasetKey)
        .collect(Collectors.toList());
    return new AssemblyState(vals, valOrZero(failed, datasetKey), valOrZero(counter, datasetKey));
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

  public void sync(int catalogueKey, RequestScope request, User user) throws IllegalArgumentException {
    nameIndex.assertOnline();
    if (request.getSectorKey() != null) {
      syncSector(DSID.of(catalogueKey, request.getSectorKey()), user);
    } else if (request.getDatasetKey() != null) {
      LOG.info("Sync all sectors in source dataset {}", request.getDatasetKey());
      final AtomicInteger cnt = new AtomicInteger();
      try (SqlSession session = factory.openSession(true)) {
        SectorMapper sm = session.getMapper(SectorMapper.class);
        sm.processSectors(catalogueKey, request.getDatasetKey()).forEach(s -> {
          syncSector(s, user);
          cnt.getAndIncrement();
        });
      }
      // now that we have them schedule syncs
      LOG.info("Queued {} sectors from dataset {} for sync", cnt.get(), request.getDatasetKey());
    } else if (request.getAll()) {
      syncAll(catalogueKey, user);
    } else {
      throw new IllegalArgumentException("No sectorKey or datasetKey given in request");
    }
  }
  
  private synchronized void syncSector(DSID<Integer> sectorKey, User user) throws IllegalArgumentException {
    SectorSync ss = new SectorSync(sectorKey, factory, nameIndex, indexService, sid, this::successCallBack, this::errorCallBack, user);
    queueJob(ss);
  }

  /**
   * @param full if true does a full deletion. Otherwise higher rank taxa are kept unlinked from the sector
   */
  public void deleteSector(DSID<Integer> sectorKey, boolean full, User user) throws IllegalArgumentException {
    SectorRunnable sd;
    if (full) {
      sd = new SectorDeleteFull(sectorKey, factory, indexService, sid, this::successCallBack, this::errorCallBack, user);
    } else {
      sd = new SectorDelete(sectorKey, factory, indexService, sid, this::successCallBack, this::errorCallBack, user);
    }
    queueJob(sd);
  }
  
  private synchronized void queueJob(SectorRunnable job) throws IllegalArgumentException {
    nameIndex.assertOnline();
    // is this sector already syncing?
    if (syncs.containsKey(job.sectorKey)) {
      LOG.info("{} already busy", job.sector);
      // ignore
    
    } else {
      assertStableData(job);
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
  
  public synchronized void cancel(DSID<Integer> sectorKey, User user) {
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
        syncSector(s, user);
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
