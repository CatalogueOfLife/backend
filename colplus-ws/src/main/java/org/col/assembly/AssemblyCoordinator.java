package org.col.assembly;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.*;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.collect.Lists;
import io.dropwizard.lifecycle.Managed;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.ColUser;
import org.col.api.model.Sector;
import org.col.api.model.SectorImport;
import org.col.common.concurrent.ExecutorUtils;
import org.col.dao.DatasetImportDao;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.SectorMapper;
import org.col.es.NameUsageIndexService;
import org.col.importer.ImportManager;
import org.gbif.nameparser.utils.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.WsServer.MILLIS_TO_DIE;

public class AssemblyCoordinator implements Managed {
  private static final Logger LOG = LoggerFactory.getLogger(AssemblyCoordinator.class);
  private static final String THREAD_NAME = "assembly-sync";
  
  private ExecutorService exec;
  private ImportManager importManager;
  private final SqlSessionFactory factory;
  private final NameUsageIndexService indexService;
  private final DatasetImportDao diDao;
  private final Map<Integer, Future> syncs = new ConcurrentHashMap<Integer, Future>();
  private final Map<Integer, SectorImport> imports = new ConcurrentHashMap<>();
  private final Timer timer;
  private final Counter counter;
  private final Counter failed;
  
  public AssemblyCoordinator(SqlSessionFactory factory, DatasetImportDao diDao, NameUsageIndexService indexService, MetricRegistry registry) {
    this.factory = factory;
    this.diDao = diDao;
    this.indexService = indexService;
    timer = registry.timer("org.col.assembly.timer");
    counter = registry.counter("org.col.assembly.counter");
    failed = registry.counter("org.col.assembly.failed");
  }
  
  @Override
  public void start() throws Exception {
    LOG.info("Starting assembly coordinator");
    exec = Executors.newSingleThreadExecutor(new NamedThreadFactory(THREAD_NAME, Thread.MAX_PRIORITY, true));
  }
  
  @Override
  public void stop() throws Exception {
    // orderly shutdown running imports
    for (Future f : syncs.values()) {
      f.cancel(true);
    }
    // fully shutdown threadpool within given time
    ExecutorUtils.shutdown(exec, MILLIS_TO_DIE, TimeUnit.MILLISECONDS);
  }
  
  public void setImportManager(ImportManager importManager) {
    this.importManager = importManager;
  }
  
  public AssemblyState getState() {
    return new AssemblyState(Lists.newArrayList(imports.values()), (int) failed.getCount(), (int) counter.getCount());
  }
  
  /**
   * Check if any sector from a given dataset is currently syncing and return the sector key. Otherwise null
   */
  public Integer hasSyncingSector(int datasetKey){
    for (SectorImport si : imports.values()) {
      if (si.getDatasetKey().equals(datasetKey)) {
        return si.getSectorKey();
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
        if (importManager != null && importManager.isRunning(s.getDatasetKey())) {
          LOG.warn("Concurrently running dataset import. Cannot sync sector {} from dataset {}", s.getKey(), s.getDatasetKey());
          throw new IllegalArgumentException("Dataset "+s.getDatasetKey()+" currently being imported. Cannot sync sector " + s.getKey());
        }
        NameMapper nm = session.getMapper(NameMapper.class);
        if (nm.hasData(s.getDatasetKey())) {
          return;
        }
      } catch (PersistenceException e) {
        // missing partitions cause this
        LOG.debug("No partition exists for dataset {}", s.getDatasetKey(), e);
      }
    }
    LOG.warn("Cannot sync sector {} which has never been imported", s.getKey());
    throw new IllegalArgumentException("Dataset empty. Cannot sync sector " + s.getKey());
  }
  
  public synchronized void syncSector(int sectorKey, ColUser user) throws IllegalArgumentException {
    // is this sector already syncing?
    if (syncs.containsKey(sectorKey)) {
      LOG.info("Sector {} already syncing", sectorKey);
      // ignore
      
    } else {
      Sector s = readSector(sectorKey);
      assertStableData(s);
      SectorSync ss = new SectorSync(s, factory, indexService, diDao, this::successCallBack, this::errorCallBack, user);
      syncs.put(sectorKey, exec.submit(ss));
      imports.put(sectorKey, ss.getState());
      LOG.info("Queued sync of sector {}", sectorKey);
    }
  }
  
  public synchronized void deleteSector(int sectorKey, ColUser user) {
    // is this sector already syncing?
    if (syncs.containsKey(sectorKey)) {
      LOG.info("Sector {} already busy", sectorKey);
      // ignore

    } else {
      SectorDelete sd = new SectorDelete(readSector(sectorKey), factory, indexService, this::successCallBack, this::errorCallBack, user);
      syncs.put(sectorKey, exec.submit(sd));
      imports.put(sectorKey, sd.getState());
      LOG.info("Queued deletion of sector {}", sectorKey);
    }
  }
  
  private Sector readSector(int sectorKey) {
    try (SqlSession session = factory.openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      return sm.get(sectorKey);
    }
  }
  
  /**
   * We use old school callbacks here as you cannot easily cancel CompletableFutures.
   */
  private void successCallBack(SectorRunnable sync) {
    Duration durQueued = Duration.between(sync.getCreated(), sync.getStarted());
    Duration durRun = Duration.between(sync.getStarted(), LocalDateTime.now());
    LOG.info("Sector Sync {} finished. {} min queued, {} min to execute", sync.getSectorKey(), durQueued.toMinutes(), durRun.toMinutes());
    counter.inc();
    timer.update(durRun.getSeconds(), TimeUnit.SECONDS);
    syncs.remove(sync.getSectorKey());
  }
  
  /**
   * We use old school callbacks here as you cannot easily cancel CompletableFutures.
   */
  private void errorCallBack(SectorRunnable sync, Exception err) {
    LOG.error("Sector Sync {} failed: {}", sync.getSectorKey(), err.getCause().getMessage(), err.getCause());
    failed.inc();
    syncs.remove(sync.getSectorKey());
  }
  

  
  public void cancel(int sectorKey, ColUser user) {
    if (syncs.containsKey(sectorKey)) {
      imports.get(sectorKey).setState(SectorImport.State.CANCELED);
      LOG.info("Sync of sector {} cancelled by user {}", sectorKey, user);
      syncs.get(sectorKey).cancel(true);
    }
  }
}
