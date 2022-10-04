package life.catalogue.assembly;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.search.DecisionSearchRequest;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.License;
import life.catalogue.api.vocab.Setting;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.dao.SectorDao;
import life.catalogue.dao.SectorImportDao;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;

import org.gbif.nameparser.api.Rank;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

abstract class SectorRunnable implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(SectorRunnable.class);
  private final static Set<Rank> MERGE_RANKS_DEFAULT = Set.of(Rank.FAMILY, Rank.GENUS, Rank.SPECIES,
    Rank.SUBSPECIES, Rank.VARIETY, Rank.FORM, Rank.UNRANKED);

  protected final DSID<Integer> sectorKey;
  protected final int subjectDatasetKey;
  protected Sector sector;
  final boolean validateSector;
  final SqlSessionFactory factory;
  final NameUsageIndexService indexService;
  final SectorDao dao;
  final SectorImportDao sid;
  // maps keyed on taxon ids from this sector
  final Map<String, EditorialDecision> decisions = new HashMap<>();
  List<Sector> childSectors;
  // map with foreign child id to original parent name
  Map<String, Name> foreignChildrenParents = new HashMap<>();
  private final UsageMatcherGlobal matcher;
  private final boolean clearMatcherCache;
  private final Consumer<SectorRunnable> successCallback;
  private final BiConsumer<SectorRunnable, Exception> errorCallback;
  private final LocalDateTime created = LocalDateTime.now();
  final User user;
  final SectorImport state;

  /**
   * @throws IllegalArgumentException if the sectors dataset is not of PROJECT origin
   */
  SectorRunnable(DSID<Integer> sectorKey, boolean validateSector, boolean validateLicenses, boolean clearMatcherCache, SqlSessionFactory factory,
                 UsageMatcherGlobal matcher, NameUsageIndexService indexService, SectorDao dao, SectorImportDao sid,
                 Consumer<SectorRunnable> successCallback, BiConsumer<SectorRunnable, Exception> errorCallback, User user) throws IllegalArgumentException {
    this.user = Preconditions.checkNotNull(user);
    this.matcher = matcher;
    this.clearMatcherCache = clearMatcherCache;
    this.validateSector = validateSector;
    this.factory = factory;
    this.indexService = indexService;
    this.dao = dao;
    this.sid = sid;
    this.successCallback = successCallback;
    this.errorCallback = errorCallback;
    this.sectorKey = sectorKey;

    // create new sync metrics instance
    state = new SectorImport();
    state.setSectorKey(sectorKey.getId());
    state.setDatasetKey(sectorKey.getDatasetKey());
    state.setJob(getClass().getSimpleName());
    state.setState(ImportState.WAITING);
    state.setCreatedBy(user.getKey());

    // check for existence and datasetKey - we will load the real thing for processing only when we get executed!
    sector = loadSectorAndUpdateDatasetImport(false);
    this.subjectDatasetKey = sector.getSubjectDatasetKey();
    try (SqlSession session = factory.openSession(true)) {
      // make sure the target dataset is a PROJECT
      Dataset target = session.getMapper(DatasetMapper.class).get(sectorKey.getDatasetKey());
      if (target.getOrigin() != DatasetOrigin.PROJECT) {
        throw new IllegalArgumentException("Cannot run a " + getClass().getSimpleName() + " against a " + target.getOrigin() + " dataset");
      }
      if (validateLicenses) {
        Dataset source = session.getMapper(DatasetMapper.class).get(subjectDatasetKey);
        if (!License.isCompatible(source.getLicense(), target.getLicense())) {
          LOG.warn("Source license {} of {} is not compatible with license {} of project {}", source.getLicense(), sector, target.getLicense(), sectorKey.getDatasetKey());
          // we should be throwsig exceptions when people try to combine incompatible data
          // COL unfortunaltey still uses the OTHER license which is always incompatible, so we exclude that from throwing for now
          //TODO: activate all exception when licensing consultations have been finished
          if (target.getLicense().isCreativeCommons()) {
            throw new IllegalArgumentException("Source license " +source.getLicense()+ " is not compatible with license " +target.getLicense()+ " of project " + sectorKey.getDatasetKey());
          }
        }
      }
      // finally create the sync metrics record with a new attempt
      session.getMapper(SectorImportMapper.class).create(state);
    }
  }
  
  @Override
  public void run() {
    LoggingUtils.setSectorMDC(sectorKey, state.getAttempt(), getClass());
    boolean failed = true;
    try {
      state.setStarted(LocalDateTime.now());
      state.setState( ImportState.PREPARING);
      LOG.info("Start {} for sector {}", this.getClass().getSimpleName(), sectorKey);
      init();

      // clear matcher cache?
      if (clearMatcherCache) {
        matcher.clear(sectorKey.getDatasetKey());
      }

      doWork();

      state.setState( ImportState.ANALYZING);
      LOG.info("Build metrics for sector {}", sectorKey);
      doMetrics();

      state.setState( ImportState.INDEXING);
      LOG.info("Update search index for sector {}", sectorKey);
      updateSearchIndex();

      state.setState( ImportState.FINISHED);
      LOG.info("Completed {} for sector {}", this.getClass().getSimpleName(), sectorKey);
      failed = false;
      successCallback.accept(this);

    } catch (InterruptedException e) {
      LOG.warn("Interrupted {}", this, e);
      state.setState(ImportState.CANCELED);
      errorCallback.accept(this, e);

    } catch (Exception e) {
      LOG.error("Failed {}", this, e);
      state.setError(ExceptionUtils.getRootCauseMessage(e));
      state.setState(ImportState.FAILED);
      errorCallback.accept(this, e);

    } finally {
      state.setFinished(LocalDateTime.now());
      // persist sector import
      try (SqlSession session = factory.openSession(true)) {
        session.getMapper(SectorImportMapper.class).update(state);
        // update sector with latest attempt on success only for true syncs
        if (!failed && this instanceof SectorSync) {
          session.getMapper(SectorMapper.class).updateLastSync(sectorKey, state.getAttempt());
        }
      }
      LoggingUtils.removeSectorMDC();
    }
  }

  /**
   * The default runnable does not load attached sectors
   * @throws Exception
   */
  void init() throws Exception {
    init(false);
  }

  void init(boolean loadChildSectors) throws Exception {
    // load latest version of the sector again to get the latest target ids
    sector = loadSectorAndUpdateDatasetImport(validateSector);
    loadDecisions();
    if (loadChildSectors) {
      loadAttachedSectors();
    }
    checkIfCancelled();
  }
  
  protected Sector loadSectorAndUpdateDatasetImport(boolean validate) {
    try (SqlSession session = factory.openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      Sector s = sm.get(sectorKey);
      if (s == null) {
        throw new NotFoundException("Sector "+sectorKey+" does not exist");
      }
      // apply dataset defaults if needed
      if (s.getEntities() == null || s.getEntities().isEmpty() || s.getRanks() == null || s.getRanks().isEmpty()) {
        DatasetSettings ds = ObjectUtils.coalesce(
          session.getMapper(DatasetMapper.class).getSettings(sectorKey.getDatasetKey()),
          new DatasetSettings()
        );

        if (s.getEntities() == null || s.getEntities().isEmpty()) {
          if (ds.has(Setting.SECTOR_ENTITIES)) {
            s.setEntities(Set.copyOf(ds.getEnumList(Setting.SECTOR_ENTITIES)));
          } else {
            s.setEntities(Collections.emptySet());
          }
        }

        if (s.getRanks() == null || s.getRanks().isEmpty()) {
          if(s.getMode() == Sector.Mode.MERGE) {
            // in merge mode we dont want any higher ranks than family by default!
            s.setRanks(MERGE_RANKS_DEFAULT);

          } else if (ds.has(Setting.SECTOR_RANKS)) {
            s.setRanks(Set.copyOf(ds.getEnumList(Setting.SECTOR_RANKS)));

          } else {
            // all
            s.setRanks(Set.of(Rank.values()));
          }
        }
      }
      if (validate) {
        // assert that target actually exists. Subject might be bad - not needed for deletes!
        TaxonMapper tm = session.getMapper(TaxonMapper.class);

        SectorDao.verifyTaxon(s, "subject", s::getSubjectAsDSID, tm);
        SectorDao.verifyTaxon(s, "target", s::getTargetAsDSID, tm);
      }
      // load current dataset import
      var datasetImport = session.getMapper(DatasetImportMapper.class).last(subjectDatasetKey);
      if (datasetImport != null) {
        state.setDatasetAttempt(datasetImport.getAttempt());
      }

      return s;
    }
  }
  
  private void loadDecisions() {
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(DecisionMapper.class).processSearch(DecisionSearchRequest.byDataset(sectorKey.getDatasetKey(), subjectDatasetKey)).forEach(ed -> {
        decisions.put(ed.getSubject().getId(), ed);
      });
    }
    LOG.info("Loaded {} editorial decisions for sector {}", decisions.size(), sectorKey);
  }
  
  private void loadAttachedSectors() {
    try (SqlSession session = factory.openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      childSectors=sm.listChildSectors(sectorKey);
    }
    LOG.info("Loaded {} sectors targeting taxa from sector {}", childSectors.size(), sectorKey);
  }
  
  abstract void doWork() throws Exception;

  abstract void doMetrics() throws Exception;

  abstract void updateSearchIndex() throws Exception;
  
  public SectorImport getState() {
    return state;
  }
  
  public DSID<Integer> getSectorKey() {
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
        ", subjectDatasetKey=" + subjectDatasetKey +
        ", sector=" + sector +
        ", created=" + created +
        " by " + (user == null ? "?" : user.getUsername()) +
        '}';
  }
}
