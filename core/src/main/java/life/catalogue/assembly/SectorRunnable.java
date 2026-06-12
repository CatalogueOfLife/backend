package life.catalogue.assembly;

import life.catalogue.api.event.DatasetDataChanged;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.search.DecisionSearchRequest;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.*;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobLane;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dao.SectorDao;
import life.catalogue.dao.SectorImportDao;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.*;
import life.catalogue.es.indexing.NameUsageIndexService;
import life.catalogue.event.EventBroker;

import org.gbif.nameparser.api.Rank;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class SectorRunnable extends BackgroundJob {
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
  private final EventBroker bus;
  private final @Nullable SyncCounter counter;
  final int user;
  final SectorImport state;
  final boolean updateSectorAttemptOnSuccess;

  /**
   * @throws IllegalArgumentException if the sector key is not of PROJECT origin
   */
  SectorRunnable(DSID<Integer> sectorKey, boolean validateSector, SqlSessionFactory factory,
                 NameUsageIndexService indexService, SectorDao dao, SectorImportDao sid, EventBroker bus,
                 @Nullable SyncCounter counter, boolean updateSectorAttemptOnSuccess, int user) throws IllegalArgumentException {
    super(user);
    // make sure the sector is a project sector, not from a release
    if (sectorKey.getDatasetKey() == null || !DatasetInfoCache.CACHE.info(sectorKey.getDatasetKey()).isProject()) {
      throw new IllegalArgumentException("Sector required to be a project dataset key");
    }
    this.updateSectorAttemptOnSuccess = updateSectorAttemptOnSuccess;
    this.user = user;
    this.bus = bus;
    this.counter = counter;
    this.validateSector = validateSector;
    this.factory = factory;
    this.indexService = indexService;
    this.dao = dao;
    this.sid = sid;
    this.sectorKey = sectorKey;

    // create new sync metrics instance
    state = new SectorImport();
    state.setSectorKey(sectorKey.getId());
    state.setDatasetKey(sectorKey.getDatasetKey());
    state.setJobKey(getKey());
    state.setJob(getJobName());
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

  @Override
  public JobLane getLane() {
    return JobLane.SYNC;
  }

  /**
   * Serialize all sector jobs by their project, so only one sector of the same project is ever synced at a time
   * while sectors of different projects may run in parallel.
   */
  @Override
  public Object getSerialBy() {
    return sectorKey.getDatasetKey();
  }

  @Override
  public Integer datasetKey() {
    return sectorKey.getDatasetKey();
  }

  @Override
  public Integer sectorKey() {
    return sectorKey.getId();
  }

  @Override
  public Object getParams() {
    return new SyncParams(sectorKey.getDatasetKey(), sectorKey.getId(), subjectDatasetKey);
  }

  public record SyncParams(int datasetKey, int sectorKey, int subjectDatasetKey) {
  }

  /**
   * There can only be a single job for the same sector queued or running.
   */
  @Override
  public boolean isDuplicate(BackgroundJob other) {
    return other instanceof SectorRunnable && ((SectorRunnable) other).sectorKey.equals(this.sectorKey);
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

  /**
   * Runs the job directly on the callers thread, outside of the job executor and the background job lifecycle.
   * No generic job record is persisted and no notifications are sent, but the sector_import metrics are tracked as usual.
   * Exceptions are not propagated - inspect getState() for the outcome.
   * Used by the XRelease to merge sectors inside the running release job itself.
   */
  public void runEmbedded() {
    try {
      execute();
    } catch (Exception e) {
      // the sector import state was updated and the cause recorded by execute() already
      LOG.error("Embedded {} of sector {} failed", getClass().getSimpleName(), sectorKey, e);
    }
  }

  @Override
  public void execute() throws Exception {
    try {
      LoggingUtils.setSourceMDC(subjectDatasetKey);
      LoggingUtils.setSectorMDC(sectorKey, state.getAttempt());
      state.setStarted(LocalDateTime.now());
      updateState(ImportState.PREPARING);
      LOG.info("Start {} for sector {}", this.getClass().getSimpleName(), sectorKey);
      init();

      doWork();

      updateState(ImportState.ANALYZING);
      LOG.info("Build metrics for sector {}", sectorKey);
      doMetrics();

      updateState(ImportState.INDEXING);
      LOG.info("Update search index for sector {}", sectorKey);
      updateSearchIndex();

      state.setState(ImportState.FINISHED);
      LOG.info("Completed {} for sector {} with {} names and {} usages", this.getClass().getSimpleName(), sectorKey, state.getNameCount(), state.getUsagesCount());
      bus.publish(new DatasetDataChanged(sectorKey.getDatasetKey(), user));
      if (updateSectorAttemptOnSuccess) {
        // update sector with latest attempt on success if subclass requested it
        try (SqlSession session = factory.openSession(true)) {
          session.getMapper(SectorMapper.class).updateLastSync(sectorKey, state.getAttempt());
        }
      }

    } catch (InterruptedException e) {
      LOG.warn("Interrupted {}", this, e);
      state.setState(ImportState.CANCELED);
      throw e;

    } catch (Exception e) {
      LOG.error("Failed {}", this, e);
      state.setError(ExceptionUtils.getRootCauseMessage(e));
      state.setState(ImportState.FAILED);
      throw e;

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

  private void updateState(ImportState state) throws InterruptedException {
    this.state.setState(state);
    setStep(state);
    checkIfCancelled();
  }

  @Override
  protected void onFinish() throws Exception {
    if (counter != null) {
      if (isFinished()) {
        counter.completed(sectorKey.getDatasetKey(), state.getDuration() == null ? 0 : state.getDuration() / 1000);
      } else {
        counter.failed(sectorKey.getDatasetKey());
      }
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
      if (dsInfo.origin != DatasetOrigin.PROJECT) {
        throw new IllegalArgumentException("Cannot run a " + getClass().getSimpleName() + " against a " + dsInfo.origin + " dataset");
      }
      // apply dataset defaults if needed
      DatasetSettings ds = ObjectUtils.coalesce(
        session.getMapper(DatasetMapper.class).getSettings(dsInfo.keyOrProjectKey()),
        new DatasetSettings()
      );

      if (ds.has(Setting.SECTOR_CREATE_IMPLICIT_NAMES)) {
        s.setCreateImplicitNames(ds.getBoolDefault(Setting.SECTOR_CREATE_IMPLICIT_NAMES, true));
      }
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
      if (validate) {
        // assert that target & subject actually exist
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

  @Override
  public String toString() {
    return this.getClass().getSimpleName() + "{" +
        "sectorKey=" + sectorKey +
        ", subjectDatasetKey=" + subjectDatasetKey +
        ", sector=" + sector +
        ", created=" + getCreated() +
        " by " + user +
        '}';
  }
}
