package org.col.assembly;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.*;
import org.col.api.util.ObjectUtils;
import org.col.api.vocab.Datasets;
import org.col.common.util.LoggingUtils;
import org.col.db.mapper.DecisionMapper;
import org.col.db.mapper.SectorImportMapper;
import org.col.db.mapper.SectorMapper;
import org.col.db.mapper.TaxonMapper;
import org.col.es.name.index.NameUsageIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class SectorRunnable implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(SectorRunnable.class);

  final int catalogueKey = Datasets.DRAFT_COL;
  final int datasetKey;
  final Sector sector;
  final SqlSessionFactory factory;
  final NameUsageIndexService indexService;
  // maps keyed on taxon ids from this sector
  final Map<String, EditorialDecision> decisions = new HashMap<>();
  List<Sector> childSectors;
  List<Taxon> foreignChildren;
  // map with foreign child id to original parent name
  Map<String, Name> foreignChildrenParents = new HashMap<>();
  final Consumer<SectorRunnable> successCallback;
  final BiConsumer<SectorRunnable, Exception> errorCallback;
  final LocalDateTime created = LocalDateTime.now();
  final ColUser user;
  final SectorImport state = new SectorImport();
  
  SectorRunnable(Sector s, SqlSessionFactory factory, NameUsageIndexService indexService,
                      Consumer<SectorRunnable> successCallback,
                      BiConsumer<SectorRunnable, Exception> errorCallback, ColUser user) throws IllegalArgumentException {
    this.user = Preconditions.checkNotNull(user);
    try (SqlSession session = factory.openSession(true)) {
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      // check if sector actually exists
      this.sector = ObjectUtils.checkNotNull(s, "Sector required");
      this.datasetKey = sector.getDatasetKey();
      // #ssert that target actually exists. Subject might be bad - not needed for deletes!
      assertTargetID(tm);
      // lookup next attempt
      List<SectorImport> imports = session.getMapper(SectorImportMapper.class).list(s.getKey(), null,null, new Page(0,1));
      state.setAttempt(imports == null || imports.isEmpty() ? 1 : imports.get(0).getAttempt() + 1);
      state.setSectorKey(s.getKey());
      state.setDatasetKey(datasetKey);
      state.setType(getClass().getSimpleName());
      state.setState(SectorImport.State.WAITING);
    }
    this.factory = factory;
    this.indexService = indexService;
    this.successCallback = successCallback;
    this.errorCallback = errorCallback;
  }
  
  private Taxon assertTargetID(TaxonMapper tm) throws IllegalArgumentException {
    ObjectUtils.checkNotNull(sector.getTarget(), sector + " does not have any target");
    // check if target actually exists
    return ObjectUtils.checkNotNull(tm.get(catalogueKey, sector.getTarget().getId()), "Sector " + sector.getKey() + " does have a non existing target id");
  }
  
  void assertSubjectID() throws IllegalArgumentException {
    try (SqlSession session = factory.openSession(true)) {
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      // check if subject actually exists
      ObjectUtils.checkNotNull(sector.getSubject(), sector + " does not have any subject");
      String msg = "Sector " + sector.getKey() + " does have a non existing subject " + sector.getSubject() + " for dataset " + sector.getDatasetKey();
      try {
        ObjectUtils.checkNotNull(tm.get(sector.getDatasetKey(), sector.getSubject().getId()), msg);
      } catch (PersistenceException e) {
        throw new IllegalArgumentException(msg, e);
      }
    }
  }
  
  @Override
  public void run() {
    LoggingUtils.setSectorMDC(sector.getKey(), state.getAttempt(), getClass());
    try {
      state.setStarted(LocalDateTime.now());
      init();
      doWork();
      successCallback.accept(this);
      
    } catch (InterruptedException e) {
      LOG.warn("Interrupted {}", this, e);
      errorCallback.accept(this, e);
      state.setState(SectorImport.State.CANCELED);
      
    } catch (Exception e) {
      LOG.error("Error running {}", this, e);
      state.setError(ExceptionUtils.getRootCauseMessage(e));
      errorCallback.accept(this, e);
      state.setState(SectorImport.State.FAILED);
      
    } finally {
      LOG.info("Completed {}", this);
      state.setFinished(LocalDateTime.now());
      finalWork();
      LoggingUtils.removeSectorMDC();
    }
  }
  
  abstract void finalWork();
  
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
      for (EditorialDecision ed : dm.listByDataset(datasetKey, null)) {
        decisions.put(ed.getSubject().getId(), ed);
      }
    }
    LOG.info("Loaded {} editorial decisions for sector {}", decisions.size(), sector.getKey());
  }
  
  private void loadForeignChildren() {
    try (SqlSession session = factory.openSession(true)) {
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      foreignChildren = tm.foreignChildren(catalogueKey, sector.getKey());
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
  
  @Override
  public String toString() {
    return this.getClass().getSimpleName() + "{" +
        "datasetKey=" + datasetKey +
        ", sector=" + sector +
        ", created=" + created +
        " by " + (user == null ? "?" : user.getUsername()) +
        '}';
  }
}
