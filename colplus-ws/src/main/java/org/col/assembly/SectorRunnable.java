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
import org.col.api.model.ColUser;
import org.col.api.model.EditorialDecision;
import org.col.api.model.Name;
import org.col.api.model.Page;
import org.col.api.model.Sector;
import org.col.api.model.SectorImport;
import org.col.api.model.Taxon;
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
  protected final int datasetKey;
  protected final int sectorKey;
  protected Sector sector;
  final boolean validateSector;
  final SqlSessionFactory factory;
  final NameUsageIndexService indexService;
  // maps keyed on taxon ids from this sector
  final Map<String, EditorialDecision> decisions = new HashMap<>();
  List<Sector> childSectors;
  List<Taxon> foreignChildren;
  // map with foreign child id to original parent name
  Map<String, Name> foreignChildrenParents = new HashMap<>();
  private final Consumer<SectorRunnable> successCallback;
  private final BiConsumer<SectorRunnable, Exception> errorCallback;
  private final LocalDateTime created = LocalDateTime.now();
  final ColUser user;
  final SectorImport state = new SectorImport();
  
  SectorRunnable(int sectorKey, boolean validateSector, SqlSessionFactory factory, NameUsageIndexService indexService,
                      Consumer<SectorRunnable> successCallback,
                      BiConsumer<SectorRunnable, Exception> errorCallback, ColUser user) throws IllegalArgumentException {
    this.user = Preconditions.checkNotNull(user);
    this.validateSector = validateSector;
    this.factory = factory;
    this.sectorKey = sectorKey;
    try (SqlSession session = factory.openSession(true)) {
      // check for existance and datasetKey - we will load the real thing for processing only when we get executed!
      sector = loadSector(false);
      this.datasetKey = sector.getSubjectDatasetKey();
      // lookup next attempt
      List<SectorImport> imports = session.getMapper(SectorImportMapper.class).list(sectorKey, null, null,null, new Page(0,1));
      state.setAttempt(imports == null || imports.isEmpty() ? 1 : imports.get(0).getAttempt() + 1);
      state.setSectorKey(sectorKey);
      state.setDatasetKey(datasetKey);
      state.setType(getClass().getSimpleName());
      state.setState(SectorImport.State.WAITING);
    }
    this.indexService = indexService;
    this.successCallback = successCallback;
    this.errorCallback = errorCallback;
  }
  
  @Override
  public void run() {
    LoggingUtils.setSectorMDC(sectorKey, state.getAttempt(), getClass());
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
    // load latest version of the sector again to get the latest target ids
    sector = loadSector(validateSector);
    loadDecisions();
    loadForeignChildren();
    loadAttachedSectors();
    checkIfCancelled();
  }
  
  private Sector loadSector(boolean validate) {
    try (SqlSession session = factory.openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      Sector s = sm.get(sectorKey);
      if (s == null) {
        throw new IllegalArgumentException("Sector "+sectorKey+" does not exist");
      }
      
      if (validate) {
        // assert that target actually exists. Subject might be bad - not needed for deletes!
        TaxonMapper tm = session.getMapper(TaxonMapper.class);
        
        // check if target actually exists
        String msg = "Sector " + s.getKey() + " does have a non existing target " + s.getTarget() + " for dataset " + catalogueKey;
        try {
          ObjectUtils.checkNotNull(s.getTarget(), s + " does not have any target");
          ObjectUtils.checkNotNull(tm.get(s.getTargetAsDSID()), "Sector " + s.getKey() + " does have a non existing target id");
        } catch (PersistenceException e) {
          throw new IllegalArgumentException(msg, e);
        }
  
        // also validate the subject for syncs
        msg = "Sector " + s.getKey() + " does have a non existing subject " + s.getSubject() + " for dataset " + datasetKey;
        try {
          ObjectUtils.checkNotNull(s.getSubject(), s + " does not have any subject");
          ObjectUtils.checkNotNull(tm.get(s.getSubjectAsDSID()), msg);
        } catch (PersistenceException e) {
          throw new IllegalArgumentException(msg, e);
        }
      }
      return s;
    }
  }
  
  private void loadDecisions() {
    try (SqlSession session = factory.openSession(true)) {
      DecisionMapper dm = session.getMapper(DecisionMapper.class);
      for (EditorialDecision ed : dm.listBySubjectDataset(catalogueKey, datasetKey, null)) {
        decisions.put(ed.getSubject().getId(), ed);
      }
    }
    LOG.info("Loaded {} editorial decisions for sector {}", decisions.size(), sectorKey);
  }
  
  private void loadForeignChildren() {
    try (SqlSession session = factory.openSession(true)) {
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      foreignChildren = tm.foreignChildren(catalogueKey, sectorKey);
    }
    LOG.info("Loaded {} children from other sectors with a parent from sector {}", foreignChildren.size(), sectorKey);
  }
  
  private void loadAttachedSectors() {
    try (SqlSession session = factory.openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      childSectors=sm.listChildSectors(Datasets.DRAFT_COL, sectorKey);
    }
    LOG.info("Loaded {} sectors targeting taxa from sector {}", childSectors.size(), sectorKey);
  }
  
  abstract void doWork() throws Exception;
  
  public SectorImport getState() {
    return state;
  }
  
  public Integer getSectorKey() {
    return sectorKey;
  }
  
  public LocalDateTime getCreated() {
    return created;
  }
  
  public LocalDateTime getStarted() {
    return state.getStarted();
  }
  
  void checkIfCancelled() throws InterruptedException {
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedException("Sync of sector " + sectorKey + " was cancelled");
    }
  }
  
  @Override
  public String toString() {
    return this.getClass().getSimpleName() + "{" +
        "sectorKey=" + sectorKey +
        "datasetKey=" + datasetKey +
        ", sector=" + sector +
        ", created=" + created +
        " by " + (user == null ? "?" : user.getUsername()) +
        '}';
  }
}
