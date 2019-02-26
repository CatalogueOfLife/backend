package org.col.admin.assembly;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.*;
import org.col.api.vocab.Datasets;
import org.col.common.util.LoggingUtils;
import org.col.db.mapper.DecisionMapper;
import org.col.db.mapper.SectorImportMapper;
import org.col.db.mapper.SectorMapper;
import org.col.db.mapper.TaxonMapper;
import org.col.es.NameUsageIndexServiceEs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class SectorRunnable implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(SectorRunnable.class);

  final int catalogueKey = Datasets.DRAFT_COL;
  final int datasetKey;
  final Sector sector;
  final SqlSessionFactory factory;
  final NameUsageIndexServiceEs indexService;
  // maps keyed on taxon ids from this sector
  final Map<String, EditorialDecision> decisions = new HashMap<>();
  final Map<String, String> foreignChildren = new HashMap<>();
  List<Sector> childSectors;
  final Consumer<SectorRunnable> successCallback;
  final BiConsumer<SectorRunnable, Exception> errorCallback;
  final LocalDateTime created = LocalDateTime.now();
  final ColUser user;
  final SectorImport state = new SectorImport();
  
  SectorRunnable(int sectorKey, SqlSessionFactory factory, NameUsageIndexServiceEs indexService,
                      Consumer<SectorRunnable> successCallback,
                      BiConsumer<SectorRunnable, Exception> errorCallback, ColUser user) {
    this.user = user;
    try (SqlSession session = factory.openSession(true)) {
      // check if sector actually exists
      Sector s = session.getMapper(SectorMapper.class).get(sectorKey);
      if (s == null) {
        throw new IllegalArgumentException("Sector "+sectorKey+" does not exist");
      }
      this.sector = s;
      this.datasetKey = sector.getDatasetKey();
      // check if target actually exists
      Taxon target = session.getMapper(TaxonMapper.class).get(catalogueKey, sector.getTarget().getId());
      if (target == null) {
        throw new IllegalStateException("Sector " + sectorKey + " does have a non existing target id for catalogue " + catalogueKey);
      }
      // lookup next attempt
      List<SectorImport> imports = session.getMapper(SectorImportMapper.class).list(sectorKey, null, new Page(0,1));
      state.setAttempt(imports == null || imports.isEmpty() ? 1 : imports.get(0).getAttempt() + 1);
      state.setSectorKey(sectorKey);
      state.setState(SectorImport.State.WAITING);
    }
    this.factory = factory;
    this.indexService = indexService;
    this.successCallback = successCallback;
    this.errorCallback = errorCallback;
  }
  
  @Override
  public void run() {
    LoggingUtils.setSectorMDC(datasetKey, 0, getClass());
    try {
      state.setStarted(LocalDateTime.now());
  
      init();
      doWork();
      successCallback.accept(this);
      
    } catch (InterruptedException e) {
      state.setState(SectorImport.State.CANCELED);
      errorCallback.accept(this, e);
      
    } catch (Exception e) {
      state.setState(SectorImport.State.FAILED);
      state.setError(e.getCause().getMessage());
      errorCallback.accept(this, e);
      
    } finally {
      state.setFinished(LocalDateTime.now());
      try (SqlSession session = factory.openSession(true)) {
        SectorImportMapper sim = session.getMapper(SectorImportMapper.class);
        sim.create(state);
      }
      LoggingUtils.removeSectorMDC();
    }
  }
  
  
  void init() throws Exception {
    state.setState( SectorImport.State.PREPARING);
    loadDecisions();
    loadForeignChildren();
    loadAttachedSectors();
    checkIfCancelled();
  }
  
  private void loadDecisions() {
    try (SqlSession session = factory.openSession(true)) {
      DecisionMapper dm = session.getMapper(DecisionMapper.class);
      for (EditorialDecision ed : dm.list(datasetKey, null)) {
        decisions.put(ed.getSubject().getId(), ed);
      }
    }
    LOG.info("Loaded {} editorial decisions for sector {}", decisions.size(), sector.getKey());
  }
  
  private void loadForeignChildren() {
    try (SqlSession session = factory.openSession(true)) {
      //TODO: implement
    }
    LOG.info("Loaded {} children from other sectors with a parent from sector {}", foreignChildren.size(), sector.getKey());
  }
  
  private void loadAttachedSectors() {
    try (SqlSession session = factory.openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      childSectors=sm.listChildSectors(sector.getKey());
    }
    LOG.info("Loaded {} sectors targeting taxa from sector {}", childSectors.size(), sector.getKey());
  }
  
  abstract void doWork() throws Exception;
  
  public SectorImport getState() {
    return state;
  }
  
  public Integer getSectorKey() {
    return sector.getKey();
  }
  
  public LocalDateTime getCreated() {
    return created;
  }
  
  public LocalDateTime getStarted() {
    return state.getStarted();
  }
  
  void checkIfCancelled() throws InterruptedException {
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedException("Sync of sector " + sector.getKey() + " was cancelled");
    }
  }
}
