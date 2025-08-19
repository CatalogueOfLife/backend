package life.catalogue.release;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.common.lang.Exceptions;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.concurrent.DatasetBlockingJob;
import life.catalogue.concurrent.JobPriority;
import life.catalogue.dao.*;
import life.catalogue.db.CopyDataset;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;

import java.time.LocalDateTime;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Validator;

import static life.catalogue.common.lang.Exceptions.interruptIfCancelled;

/**
 * Abstract job that copies a project with all its data into a new dataset
 * and allows for custom pre/post work to be done.
 */
public abstract class AbstractProjectCopy extends DatasetBlockingJob {
  protected static final Logger LOG = LoggerFactory.getLogger(AbstractProjectCopy.class);
  protected final SqlSessionFactory factory;
  protected final DatasetImportDao diDao;
  protected final DatasetDao dDao;
  protected final DatasetSourceDao srcDao;
  protected final NameUsageIndexService indexService;
  protected final Validator validator;
  protected final int user;
  protected final int projectKey;
  protected int idMapDatasetKey;
  protected final String actionName;
  protected int attempt;
  protected DatasetImport metrics;
  protected Dataset newDataset;
  protected int newDatasetKey;
  protected boolean mapIds;
  protected DatasetSettings settings;
  protected Dataset base;
  private final boolean deleteOnError;

  private static int projectKey(int baseReleaseOrProjectKey) {
    return DatasetInfoCache.CACHE.info(baseReleaseOrProjectKey).keyOrProjectKey();
  }

  public AbstractProjectCopy(String actionName, SqlSessionFactory factory, DatasetImportDao diDao, DatasetDao dDao, NameUsageIndexService indexService, Validator validator,
                             int userKey, int baseReleaseOrProjectKey, boolean mapIds, boolean deleteOnError) {
    super(projectKey(baseReleaseOrProjectKey), userKey, JobPriority.HIGH);
    var info = DatasetInfoCache.CACHE.info(baseReleaseOrProjectKey);
    this.projectKey = info.keyOrProjectKey();
    DaoUtils.requireProject(projectKey, "Only projects can be duplicated.");
    this.idMapDatasetKey = projectKey; // defaults to project key as the idmap namespace
    this.logToFile = true;
    this.deleteOnError = deleteOnError;
    this.actionName = actionName;
    this.factory = factory;
    this.diDao = diDao;
    this.dDao = dDao;
    this.srcDao = new DatasetSourceDao(factory);
    this.indexService = indexService;
    this.validator = validator;
    this.user = userKey;
    this.mapIds = mapIds;
    // load settings & configs early
    try (SqlSession session = factory.openSession(true)) {
      settings = session.getMapper(DatasetMapper.class).getSettings(projectKey);
    }
    loadConfigs(); // can throw before we create an import record and new dataset if external configs are invalid
    // load data needed for templates & license checks
    dataset = loadDataset(factory, projectKey);
    if (info.origin.isRelease()) {
      base = loadDataset(factory, baseReleaseOrProjectKey);
    }
  }

  public int getDatasetKey() {
    return projectKey;
  }

  public int getNewDatasetKey() {
    return newDatasetKey;
  }

  public DatasetImport getMetrics() {
    return metrics;
  }

  protected void loadConfigs() throws IllegalArgumentException{
    // override to load configs before the new dataset with metadata is created
  }

  protected void modifyDataset(Dataset d) {
    d.setAlias(null); // must be unique
    d.setGbifKey(null); // must be unique
    d.setGbifPublisherKey(null);
    d.setDoi(null);
    // use the current attempt which gets written into the dataset table only at the end of the (successful) job
    d.setAttempt(attempt);
    d.setNotes(String.format("Created by %s#%s %s.", getJobName(), attempt, getKey()));
  }

  /**
   * Called as the very first thing to finish the initialisation of the job.
   * This includes creating a new import metric and the new dataset
   * @throws Exception
   */
  void initJob() throws Exception {
    // new import/release attempt
    metrics = diDao.createWaiting(projectKey, this, user);
    metrics.setJob(getClass().getSimpleName());
    attempt = metrics.getAttempt();
    LoggingUtils.setDatasetMDC(projectKey, attempt, getClass());
    updateState(ImportState.PREPARING);

    // create new dataset, e.g. release
    newDataset = dDao.copy(projectKey, user, this::modifyDataset);
    newDatasetKey = newDataset.getKey();
  }

  void prepWork() throws Exception {
    // is an id mapping table needed?
    createIdMapTables();
  }

  void finalWork() throws Exception {
    // dont do nothing - override if needed
  }

  @Override
  public void runWithLock() throws Exception {
    checkIfCancelled();
    initJob();

    checkIfCancelled();
    LOG.info("{} project {} to new dataset {}", actionName, projectKey, newDatasetKey);
    // prepare new tables
    updateState(ImportState.PROCESSING);

    // are sequences in place?
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(DatasetPartitionMapper.class).createSequences(newDatasetKey);;
    }

    // call prep
    checkIfCancelled();
    prepWork();

    // copy data
    checkIfCancelled();
    copyData();

    // subclass specifics
    checkIfCancelled();
    finalWork();

    // remove sequences if not a project
    if (newDataset.getOrigin() != DatasetOrigin.PROJECT) {
      LOG.info("Removing db sequences for {} {}", newDataset.getOrigin(), newDatasetKey);
      try (SqlSession session = factory.openSession(true)) {
        session.getMapper(DatasetPartitionMapper.class).createSequences(newDatasetKey);
      }
    }

    checkIfCancelled();
    metrics();
    checkIfCancelled();

    checkIfCancelled();
    postMetrics();
    checkIfCancelled();

    try {
      // ES index
      LOG.info("Index dataset {} into ES", newDatasetKey);
      updateState(ImportState.INDEXING);
      index();
    } catch (Exception e) {
      // allow indexing to fail - sth we can do afterwards again
      LOG.error("Error indexing new dataset {} into ES. Source dataset={}", newDatasetKey, projectKey, e);
    }

    metrics.setState(ImportState.FINISHED);
    LOG.info("Successfully finished {} project {} into dataset {}", actionName, projectKey, newDatasetKey);
  }

  protected void createIdMapTables() throws InterruptedException {
    checkIfCancelled();
    if (mapIds) {
      LOG.info("Create clean id mapping tables with scope {}", idMapDatasetKey);
      try (SqlSession session = factory.openSession(true)) {
        DatasetPartitionMapper dmp = session.getMapper(DatasetPartitionMapper.class);
        DatasetPartitionMapper.IDMAP_TABLES.forEach(t -> {
          dmp.dropTable(t, idMapDatasetKey);
          dmp.createIdMapTable(t, idMapDatasetKey);
        });
      }
    }
  }

  @Override
  protected void onError(Exception e) {
    String attempt = null;
    LOG.error("Error {} project {} into dataset {}", actionName, projectKey, newDatasetKey, e);
    if (metrics != null) {
      metrics.setState(ImportState.FAILED);
      metrics.setError(Exceptions.getFirstMessage(e));
      attempt = metrics.attempt();
    }
    // cleanup failed remains?
    if (deleteOnError) {
      LOG.info("Remove failed {} dataset {} aka {}-{}", actionName, newDatasetKey, projectKey, attempt);
      dDao.delete(newDatasetKey, user);
    }
  }

  @Override
  protected void onCancel() {
    metrics.setState(ImportState.CANCELED);
    LOG.warn("Cancelled {} project {} into dataset {}", actionName, projectKey, newDatasetKey);
    // cleanup failed remains
    LOG.info("Remove failed {} dataset {} aka {}-{}", actionName, newDatasetKey, projectKey, metrics.attempt());
    dDao.delete(newDatasetKey, user);
  }

  @Override
  protected void onFinishLocked() throws Exception {
    metrics.setFinished(LocalDateTime.now());
    LOG.info("{} took {}", getClass().getSimpleName(), DurationFormatUtils.formatDuration(metrics.getDuration(), "HH:mm:ss"));
    diDao.update(metrics);
    if (mapIds) {
      LOG.info("Remove id mapping tables for scope {}", idMapDatasetKey);
      try (SqlSession session = factory.openSession(true)) {
        DatasetPartitionMapper dmp = session.getMapper(DatasetPartitionMapper.class);
        DatasetPartitionMapper.IDMAP_TABLES.forEach(t -> dmp.dropTable(t, idMapDatasetKey));
      } catch (Exception e) {
        // avoid any exceptions as it would bring down the finally block
        LOG.error("Failed to remove id mapping tables for scope {}", idMapDatasetKey, e);
      }
    }
  }

  protected void metrics() throws InterruptedException {
    LOG.info("Build import metrics for dataset " + projectKey);
    updateState(ImportState.ANALYZING);
    // update usage counter
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(DatasetPartitionMapper.class).updateUsageCounter(projectKey);
    }
    // build taxon metrics
    if (newDataset.getOrigin().isRelease()) {
      MetricsBuilder.rebuildMetrics(factory, newDatasetKey);
    }
    // create new dataset "import" metrics in mother project
    // metrics.maxClassificationDepth needs to be set before!
    diDao.updateMetrics(metrics, newDatasetKey);
    diDao.update(metrics);
  }

  protected void postMetrics() {
    // nothing by default
  }

  protected void copyData() throws InterruptedException {
    LOG.info("Copy data into dataset {}", newDatasetKey);
    updateState(ImportState.INSERTING);
    try (SqlSession session = factory.openSession(true)) {
      copyTable(Sector.class, SectorMapper.class, session);
      copyTable(EditorialDecision.class, DecisionMapper.class, session);
      copyTable(SpeciesEstimate.class, EstimateMapper.class, session);
      copyTable(Publisher.class, PublisherMapper.class, session);

      copyTable(VerbatimRecord.class, VerbatimRecordMapper.class, session);

      copyTable(Reference.class, ReferenceMapper.class, session);

      copyTable(Name.class, NameMapper.class, session);
      copyTable(NameMatch.class, NameMatchMapper.class, session);
      copyTable(NameRelation.class, NameRelationMapper.class, session);
      copyTable(TypeMaterial.class, TypeMaterialMapper.class, session);

      copyTable(NameUsage.class, NameUsageMapper.class, session);
      copyTable(VerbatimSource.class, VerbatimSourceMapper.class, session);

      copyTable(VernacularName.class, VernacularNameMapper.class, session);
      copyTable(Distribution.class, DistributionMapper.class, session);
      copyTable(Treatment.class, TreatmentMapper.class, session);
      copyTable(Media.class, MediaMapper.class, session);
    }
  }

  void updateState(ImportState state) throws InterruptedException {
    checkIfCancelled();
    LOG.info("Change state for dataset {} to {}", newDatasetKey, state);
    metrics.setState(state);
    diDao.update(metrics);
  }

  void updateDataset(Dataset d) {
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(DatasetMapper.class).update(d);
    }
  }

  protected void index() {
    LOG.info("Build search index for dataset " + newDatasetKey);
    indexService.indexDataset(newDatasetKey);
  }

  <M extends CopyDataset> void copyTable(Class entity, Class<M> mapperClass, SqlSession session) throws InterruptedException {
    checkIfCancelled();
    int count = session.getMapper(mapperClass).copyDataset(projectKey, newDatasetKey, mapIds);
    LOG.info("Copied {} {}s from {} to {}", count, entity.getSimpleName(), projectKey, newDatasetKey);
  }

  public int getAttempt() {
    return attempt;
  }

  public Dataset getNewDataset() {
    return newDataset;
  }
}
