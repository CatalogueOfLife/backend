package life.catalogue.release;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.common.lang.Exceptions;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.dao.*;
import life.catalogue.db.CopyDataset;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

import static life.catalogue.common.lang.Exceptions.interruptIfCancelled;

/**
 * Abstract Runnable that copies a project with all its data into a new dataset
 * and allows for custom pre/post work to be done.
 */
public abstract class AbstractProjectCopy implements Runnable {
  protected final Logger LOG = LoggerFactory.getLogger(getClass());
  protected final SqlSessionFactory factory;
  protected final DatasetImportDao diDao;
  protected final DatasetDao dDao;
  protected final DatasetSourceDao srcDao;
  protected final NameUsageIndexService indexService;
  protected final int user;
  protected final int datasetKey;
  protected final int attempt;
  protected final DatasetImport metrics;
  protected final String actionName;
  protected final Dataset newDataset;
  protected final int newDatasetKey;
  private final DatasetOrigin newDatasetOrigin;
  protected final boolean mapIds;
  protected DatasetSettings settings;


  public AbstractProjectCopy(String actionName, SqlSessionFactory factory, DatasetImportDao diDao, DatasetDao dDao, NameUsageIndexService indexService,
                             int userKey, int datasetKey, boolean mapIds) {
    DaoUtils.requireManaged(datasetKey, "Only managed datasets can be duplicated.");
    this.actionName = actionName;
    this.factory = factory;
    this.diDao = diDao;
    this.dDao = dDao;
    this.srcDao = new DatasetSourceDao(factory);
    this.indexService = indexService;
    this.user = userKey;
    this.mapIds = mapIds;
    this.datasetKey = datasetKey;
    metrics = diDao.createWaiting(datasetKey, this, userKey);
    metrics.setJob(getClass().getSimpleName());
    attempt = metrics.getAttempt();
    newDataset = dDao.copy(datasetKey, userKey, this::modifyDataset);
    newDatasetKey = newDataset.getKey();
    newDatasetOrigin = newDataset.getOrigin();
  }

  protected void modifyDataset(Dataset d, DatasetSettings ds) {
    d.setAlias(null); // must be unique
    d.setGbifKey(null); // must be unique
    d.setGbifPublisherKey(null);
    d.setDoi(null);
    // use the current attempt which gets written into the dataset table only at the end of the (successful) job
    d.setAttempt(attempt);
  }

  public int getDatasetKey() {
    return datasetKey;
  }

  public int getNewDatasetKey() {
    return newDatasetKey;
  }

  public DatasetImport getMetrics() {
    return metrics;
  }

  void prepWork() throws Exception {
    // dont do nothing - override if needed
  }

  void finalWork() throws Exception {
    // dont do nothing - override if needed
  }

  /**
   * Called in case the job fails to run specific cleanups in subclasses
   */
  void onError() {
    // dont do nothing - override if needed
  }

  @Override
  public void run() {
    LoggingUtils.setDatasetMDC(datasetKey, getClass());
    try {
      LOG.info("{} project {} to new dataset {}", actionName, datasetKey, getNewDatasetKey());
      // prepare new tables
      updateState(ImportState.PROCESSING);
      Partitioner.partition(factory, newDatasetKey, newDatasetOrigin);
      // is an id mapping table needed?
      if (mapIds) {
        LOG.info("Create clean id mapping tables for project {}", datasetKey);
        try (SqlSession session = factory.openSession(true)) {
          DatasetPartitionMapper dmp = session.getMapper(DatasetPartitionMapper.class);
          DatasetPartitionMapper.IDMAP_TABLES.forEach(t -> {
            dmp.deleteTable(t, datasetKey);
            dmp.createIdMapTable(t, datasetKey);
          });
        }
      }

      // load settings
      try (SqlSession session = factory.openSession(true)) {
        settings = session.getMapper(DatasetMapper.class).getSettings(datasetKey);
      }

      // call prep
      prepWork();

      // copy data
      copyData();

      // build indices and attach partition
      Partitioner.attach(factory, newDatasetKey, newDatasetOrigin);
      if (newDatasetOrigin == DatasetOrigin.MANAGED) {
        Partitioner.createManagedObjects(factory, newDatasetKey);
      }

      // subclass specifics
      finalWork();

      metrics();

      try {
        // ES index
        LOG.info("Index dataset {} into ES", newDatasetKey);
        updateState(ImportState.INDEXING);
        index();
      } catch (RuntimeException e) {
        // allow indexing to fail - sth we can do afterwards again
        LOG.error("Error indexing new dataset {} into ES. Source dataset={}", newDatasetKey, datasetKey, e);
      }

      metrics.setState(ImportState.FINISHED);
      LOG.info("Successfully finished {} project {} into dataset {}", actionName,  datasetKey, newDatasetKey);

    } catch (Exception e) {
      metrics.setState(ImportState.FAILED);
      metrics.setError(Exceptions.getFirstMessage(e));
      LOG.error("Error {} project {} into dataset {}", actionName, datasetKey, newDatasetKey, e);

      // cleanup failed remains
      LOG.info("Remove failed {} dataset {} aka {}-{}", actionName, newDatasetKey, datasetKey, metrics.attempt(), e);
      dDao.delete(newDatasetKey, user);
      onError();

    } finally {
      metrics.setFinished(LocalDateTime.now());
      diDao.update(metrics);

      if (mapIds) {
        LOG.info("Remove id mapping tables for project {}", datasetKey);
        try (SqlSession session = factory.openSession(true)) {
          DatasetPartitionMapper dmp = session.getMapper(DatasetPartitionMapper.class);
          DatasetPartitionMapper.IDMAP_TABLES.forEach(t -> dmp.deleteTable(t, datasetKey));
        } catch (Exception e) {
          // avoid any exceptions as it would bring down the finally block
          LOG.error("Failed to remove id mapping tables for project {}", datasetKey, e);
        }
      }
      LoggingUtils.removeDatasetMDC();
    }
  }

  private void metrics() {
    LOG.info("Build import metrics for dataset " + datasetKey);
    updateState(ImportState.ANALYZING);
    // update usage counter
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(DatasetPartitionMapper.class).updateUsageCounter(datasetKey);
    }
    // create new dataset "import" metrics in mother project
    diDao.updateMetrics(metrics, newDatasetKey);
    diDao.update(metrics);
  }

  private void copyData() {
    LOG.info("Copy data into dataset {}", newDatasetKey);
    updateState(ImportState.INSERTING);
    try (SqlSession session = factory.openSession(true)) {
      copyTable(Sector.class, SectorMapper.class, session);
      copyTable(EditorialDecision.class, DecisionMapper.class, session);
      copyTable(SpeciesEstimate.class, EstimateMapper.class, session);

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

  void updateState(ImportState state) {
    LOG.info("Change state for dataset {} to {}", newDatasetKey, state);
    metrics.setState(state);
    diDao.update(metrics);
    interruptIfCancelled();
  }

  void updateDataset(Dataset d) {
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(DatasetMapper.class).update(d);
    }
  }

  private void index() {
    LOG.info("Build search index for dataset " + newDatasetKey);
    indexService.indexDataset(newDatasetKey);
  }

  private <M extends CopyDataset> void copyTable(Class entity, Class<M> mapperClass, SqlSession session){
    int count = session.getMapper(mapperClass).copyDataset(datasetKey, newDatasetKey, mapIds);
    LOG.info("Copied {} {}s", count, entity.getSimpleName());
  }

}
