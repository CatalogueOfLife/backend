package life.catalogue.assembly;

import com.google.common.base.Preconditions;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.search.DecisionSearchRequest;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

abstract class SectorRunnable implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(SectorRunnable.class);

  final int catalogueKey;
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
  private final boolean persistSync;
  final User user;
  final SectorImport state = new SectorImport();

  /**
   * @param persistSync if true the sync import is persisted on success and the last sync attempt is updated on the sector
   * @throws IllegalArgumentException if the sectors dataset is not of MANAGED origin
   */
  SectorRunnable(int sectorKey, boolean validateSector, SqlSessionFactory factory, NameUsageIndexService indexService,
                 Consumer<SectorRunnable> successCallback,
                 BiConsumer<SectorRunnable, Exception> errorCallback, boolean persistSync, User user) throws IllegalArgumentException {
    this.persistSync = persistSync;
    this.user = Preconditions.checkNotNull(user);
    this.validateSector = validateSector;
    this.factory = factory;
    this.sectorKey = sectorKey;
    // check for existance and datasetKey - we will load the real thing for processing only when we get executed!
    sector = loadSector(false);
    this.catalogueKey = sector.getDatasetKey();
    this.datasetKey = sector.getSubjectDatasetKey();
    try (SqlSession session = factory.openSession(true)) {
      // make sure the target catalogue is MANAGED and not RELEASED!
      Dataset d = session.getMapper(DatasetMapper.class).get(catalogueKey);
      if (d.getOrigin() != DatasetOrigin.MANAGED) {
        throw new IllegalArgumentException("Cannot run a " + getClass().getSimpleName() + " against a " + d.getOrigin() + " dataset");
      }
      state.setSectorKey(sectorKey);
      state.setDatasetKey(datasetKey);
      state.setJob(getClass().getSimpleName());
      state.setState(SectorImport.State.WAITING);
      state.setCreatedBy(user.getKey());
      session.getMapper(SectorImportMapper.class).create(state);
    }
    this.indexService = indexService;
    this.successCallback = successCallback;
    this.errorCallback = errorCallback;
  }
  
  @Override
  public void run() {
    LoggingUtils.setSectorMDC(sectorKey, state.getAttempt(), getClass());
    boolean failed = true;
    try {
      state.setStarted(LocalDateTime.now());
      init();
      doWork();
      LOG.info("Completed {}", this);
      failed = false;
      successCallback.accept(this);

    } catch (InterruptedException e) {
      LOG.warn("Interrupted {}", this, e);
      state.setState(SectorImport.State.CANCELED);
      errorCallback.accept(this, e);

    } catch (Exception e) {
      LOG.error("Failed {}", this, e);
      state.setError(ExceptionUtils.getRootCauseMessage(e));
      state.setState(SectorImport.State.FAILED);
      errorCallback.accept(this, e);

    } finally {
      state.setFinished(LocalDateTime.now());
      // persist sector import
      try (SqlSession session = factory.openSession(true)) {
        if (persistSync || failed) {
          session.getMapper(SectorImportMapper.class).update(state);
        }
        // update sector with latest attempt on success only
        if (persistSync && !failed) {
          session.getMapper(SectorMapper.class).updateLastSync(sectorKey, state.getAttempt());
        }
      }
      LoggingUtils.removeSectorMDC();
    }
  }

  void init() throws Exception {
    state.setState( SectorImport.State.PREPARING);
    // load latest version of the sector again to get the latest target ids
    sector = loadSector(validateSector);
    if (sector.getMode() == Sector.Mode.MERGE) {
      //TODO: https://github.com/Sp2000/colplus-backend/issues/509
      throw new NotImplementedException("Sector merging not implemented yet");
    }
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
        throw new NotFoundException("Sector "+sectorKey+" does not exist");
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
      session.getMapper(DecisionMapper.class).processSearch(DecisionSearchRequest.byDataset(catalogueKey, datasetKey)).forEach(ed -> {
        decisions.put(ed.getSubject().getId(), ed);
      });
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
      childSectors=sm.listChildSectors(catalogueKey, sectorKey);
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
