package life.catalogue.assembly;

import life.catalogue.api.event.DatasetDataChanged;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.search.DecisionSearchRequest;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.*;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dao.SectorDao;
import life.catalogue.dao.SectorImportDao;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.matching.UsageMatcherGlobal;

import org.gbif.nameparser.api.Rank;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;

abstract class SectorRunnable implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(SectorRunnable.class);
  private final static Set<Rank> MERGE_RANKS_DEFAULT = Set.of(
    Rank.FAMILY, Rank.GENUS, Rank.SPECIES,
    Rank.SUBSPECIES, Rank.VARIETY, Rank.FORM
  );

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
  private final UsageMatcherGlobal matcher;
  private final boolean clearMatcherCache;
  private final Consumer<SectorRunnable> successCallback;
  private final BiConsumer<SectorRunnable, Exception> errorCallback;
  private final LocalDateTime created = LocalDateTime.now();
  private final EventBus bus;
  final int user;
  final SectorImport state;
  final boolean updateSectorAttemptOnSuccess;

  /**
   * @throws IllegalArgumentException if the sectors dataset is not of PROJECT origin
   */
  SectorRunnable(DSID<Integer> sectorKey, boolean validateSector, boolean clearMatcherCache, SqlSessionFactory factory,
                 UsageMatcherGlobal matcher, NameUsageIndexService indexService, SectorDao dao, SectorImportDao sid, EventBus bus,
                 Consumer<SectorRunnable> successCallback, BiConsumer<SectorRunnable, Exception> errorCallback, boolean updateSectorAttemptOnSuccess, int user) throws IllegalArgumentException {
    this.updateSectorAttemptOnSuccess = updateSectorAttemptOnSuccess;
    this.user = user;
    this.bus = bus;
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
    state.setCreatedBy(user);

    // check for existence and datasetKey - we will load the real thing for processing only when we get executed!
    sector = loadSectorAndUpdateDatasetImport(false);
    subjectDatasetKey = sector.getSubjectDatasetKey();
    try (SqlSession session = factory.openSession(true)) {
      // make sure we can execute this runnable of the given sector, e.g. block accidental changes to immutable releases
      Dataset targetDS = session.getMapper(DatasetMapper.class).get(sectorKey.getDatasetKey());
      allowedToRun(targetDS, session) ;

      if (validateSector) {
        // we throw exceptions early when people try to combine incompatible data
        Dataset source = session.getMapper(DatasetMapper.class).get(subjectDatasetKey);
        if (!License.isCompatible(source.getLicense(), targetDS.getLicense())) {
          LOG.warn("Source license {} of {} is not compatible with license {} of project {}", source.getLicense(), sector, targetDS.getLicense(), sectorKey.getDatasetKey());
          throw new IllegalArgumentException("Source license " +source.getLicense()+ " of " + sector + " is not compatible with license " +targetDS.getLicense()+ " of project " + sectorKey.getDatasetKey());
        }
      }
      // finally create the sync metrics record with a new attempt
      session.getMapper(SectorImportMapper.class).create(state);
    }
  }

  /**
   * Make sure this sector is ok to be executed on the given dataset
   * @throws IllegalArgumentException if this is not the case
   */
  protected void allowedToRun(Dataset targetDS, SqlSession session) throws IllegalArgumentException {
    // make sure the target dataset is a PROJECT or XRELEASE
    if (targetDS.getOrigin() != DatasetOrigin.PROJECT && targetDS.getOrigin() != DatasetOrigin.XRELEASE) {
      throw new IllegalArgumentException("Cannot run a " + getClass().getSimpleName() + " against a " + targetDS.getOrigin() + " dataset");
    }
  }

  public Map<String, EditorialDecision> getDecisions() {
    return decisions;
  }

  @Override
  public void run() {
    LoggingUtils.setSectorMDC(sectorKey, state.getAttempt());
    LoggingUtils.setSourceMDC(sector.getSubjectDatasetKey());

    try {
      state.setStarted(LocalDateTime.now());
      state.setState( ImportState.PREPARING);
      LOG.info("Start {} for sector {}", this.getClass().getSimpleName(), sectorKey);
      init();

      // clear matcher cache?
      if (clearMatcherCache) {
        matcher.clear(sectorKey.getDatasetKey());
        bus.post(new DatasetDataChanged(sectorKey.getDatasetKey()));
      }

      doWork();

      state.setState( ImportState.ANALYZING);
      LOG.info("Build metrics for sector {}", sectorKey);
      doMetrics();

      state.setState( ImportState.INDEXING);
      LOG.info("Update search index for sector {}", sectorKey);
      updateSearchIndex();

      state.setState( ImportState.FINISHED);
      LOG.info("Completed {} for sector {} with {} names and {} usages", this.getClass().getSimpleName(), sectorKey, state.getNameCount(), state.getUsagesCount());
      successCallback.accept(this);
      if (updateSectorAttemptOnSuccess) {
        // update sector with latest attempt on success if subclass requested it
        try (SqlSession session = factory.openSession(true)) {
          session.getMapper(SectorMapper.class).updateLastSync(sectorKey, state.getAttempt());
        }
      }

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
      }
      LOG.info("{} took {}", getClass().getSimpleName(), DurationFormatUtils.formatDuration(state.getDuration(), "HH:mm:ss"));
      LoggingUtils.removeSourceMDC();
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
      var dsInfo = DatasetInfoCache.CACHE.info(sectorKey.getDatasetKey());
      if (dsInfo.origin != DatasetOrigin.PROJECT && dsInfo.origin != DatasetOrigin.XRELEASE) {
        throw new IllegalArgumentException("Cannot run a " + getClass().getSimpleName() + " against a " + dsInfo.origin + " dataset");
      }
      // apply dataset defaults if needed
      if (s.getEntities() == null || s.getEntities().isEmpty()
          || s.getRanks() == null || s.getRanks().isEmpty()
          || s.getNameTypes() == null || s.getNameTypes().isEmpty()
          || s.getNameStatusExclusion() == null || s.getNameStatusExclusion().isEmpty()
      ) {
        DatasetSettings ds = ObjectUtils.coalesce(
          session.getMapper(DatasetMapper.class).getSettings(dsInfo.keyOrProjectKey()),
          new DatasetSettings()
        );

        if (ds.has(Setting.SECTOR_COPY_ACCORDING_TO)) {
          s.setCopyAccordingTo(ds.getBool(Setting.SECTOR_COPY_ACCORDING_TO));
        }
        if (ds.has(Setting.SECTOR_REMOVE_ORDINALS)) {
          s.setRemoveOrdinals(ds.getBool(Setting.SECTOR_REMOVE_ORDINALS));
        }
        addProjectSettings(ds, Setting.SECTOR_ENTITIES, s::getEntities, s::setEntities);
        if (s.getEntities() == null || s.getEntities().isEmpty()) {
          // as a default sync everything
          s.setEntities(new HashSet<>(Arrays.asList(EntityType.values())));
        }
        addProjectSettings(ds, Setting.SECTOR_NAME_TYPES, s::getNameTypes, s::setNameTypes);
        addProjectSettings(ds, Setting.SECTOR_NAME_STATUS_EXCLUSION, s::getNameStatusExclusion, s::setNameStatusExclusion);

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
      var currentImp = session.getMapper(DatasetImportMapper.class).current(subjectDatasetKey);
      if (currentImp != null) {
        state.setDatasetAttempt(currentImp.getAttempt());
      }

      return s;
    }
  }

  private <T extends Enum> void addProjectSettings(DatasetSettings ds, Setting setting, Supplier<Set<T>> getter, Consumer<Set<T>> setter) {
    var val = getter.get();
    if (val == null || val.isEmpty()) {
      if (ds.has(setting)) {
        val = Set.copyOf(ds.getEnumList(setting));
      } else {
        val = Collections.emptySet();
      }
      setter.accept(val);
    }
  }

  private void loadDecisions() {
    try (SqlSession session = factory.openSession(true)) {
      PgUtils.consume(() -> session.getMapper(DecisionMapper.class).processSearch(DecisionSearchRequest.byDataset(sectorKey.getDatasetKey(), subjectDatasetKey)),
        ed -> decisions.put(ed.getSubject().getId(), ed)
      );
    }
    LOG.info("Loaded {} editorial decisions for sector {}", decisions.size(), sectorKey);
  }
  
  private void loadAttachedSectors() {
    try (SqlSession session = factory.openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      childSectors=sm.listChildSectors(sectorKey);
    }
    long mergeCnt = childSectors.stream().filter(s -> s.getMode() == Sector.Mode.MERGE).count();
    LOG.info("Loaded {} sectors incl {} merge sectors targeting taxa from sector {}", childSectors.size(), mergeCnt, sectorKey);
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
        " by " + user +
        '}';
  }
}
