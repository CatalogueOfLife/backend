package life.catalogue.release;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.common.lang.Exceptions;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.Partitioner;
import life.catalogue.db.CopyDataset;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static life.catalogue.common.lang.Exceptions.interruptIfCancelled;

/**
 * Abstract Runnable that copies a project with all its data into a new dataset
 * and allows for custom pre/post work to be done.
 */
public abstract class ProjectRunnable implements Runnable {
  protected final Logger LOG = LoggerFactory.getLogger(getClass());
  protected final SqlSessionFactory factory;
  protected final DatasetImportDao diDao;
  protected final NameUsageIndexService indexService;
  protected final int user;
  protected final int datasetKey;
  protected final DatasetImport metrics;
  protected final String actionName;
  protected final int newDatasetKey;

  public ProjectRunnable(String actionName, SqlSessionFactory factory, DatasetImportDao diDao, NameUsageIndexService indexService, int userKey, int datasetKey, Dataset newDataset) {
    this.actionName = actionName;
    this.factory = factory;
    this.diDao = diDao;
    this.indexService = indexService;
    this.user = userKey;
    this.datasetKey = datasetKey;
    metrics = diDao.createWaiting(newDataset, this, userKey);
    metrics.setJob(getClass().getSimpleName());
    newDatasetKey = newDataset.getKey();
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

  @Override
  public void run() {
    LoggingUtils.setDatasetMDC(datasetKey, getClass());
    try {
      LOG.info("{} project {} to new dataset {}", actionName, datasetKey, getNewDatasetKey());
      // prepare new tables
      updateState(ImportState.PROCESSING);
      Partitioner.partition(factory, newDatasetKey);

      prepWork();

      // copy data
      copyData();

      // build indices and attach partition
      LOG.info("Attach and index partitions for dataset {}", newDatasetKey);
      Partitioner.indexAndAttach(factory, newDatasetKey);
      // create metrics
      LOG.info("Build metrics for dataset {}", newDatasetKey);
      updateState(ImportState.ANALYZING);
      metrics();

      // subclass specifics
      finalWork();

      try {
        // ES index
        LOG.info("Index dataset {} into ES", newDatasetKey);
        updateState(ImportState.INDEXING);
        index();
      } catch (RuntimeException e) {
        // allow indexing to fail - sth we can do afterwards again
        LOG.error("Error indexing new dataset {} into ES. Source dataset={}", newDatasetKey, datasetKey, e);
      }

      updateState(ImportState.FINISHED);
      LOG.info("Successfully finished {} project {} into dataset {}", actionName,  datasetKey, newDatasetKey);

    } catch (Exception e) {
      metrics.setError(Exceptions.getFirstMessage(e));
      updateState(ImportState.FAILED);
      LOG.error("Error {} project {} into dataset {}", actionName, datasetKey, newDatasetKey, e);

      // cleanup partition which probably is not even attached and delete dataset
      Partitioner.delete(factory, newDatasetKey);
      try (SqlSession session = factory.openSession()) {
        session.getMapper(DatasetMapper.class).delete(newDatasetKey);
        session.commit();
      }

    } finally {
      ReleaseManager.releaseLock();
      LoggingUtils.removeDatasetMDC();
    }
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
      copyTable(NameRelation.class, NameRelationMapper.class, session);
      copyTable(TypeMaterial.class, TypeMaterialMapper.class, session);

      copyTable(NameUsage.class, NameUsageMapper.class, session);

      copyTable(VernacularName.class, VernacularNameMapper.class, session);
      copyTable(Distribution.class, DistributionMapper.class, session);
      copyTable(Description.class, DescriptionMapper.class, session);
      copyTable(Media.class, MediaMapper.class, session);
    }
  }

  void updateState(ImportState state) {
    metrics.setState(state);
    diDao.update(metrics);
    interruptIfCancelled();
  }

  private void index() {
    LOG.info("Build search index for dataset " + newDatasetKey);
    indexService.indexDataset(newDatasetKey);
  }

  private void metrics() {
    LOG.info("Build import metrics for dataset " + newDatasetKey);
    diDao.updateMetrics(metrics);
    diDao.update(metrics);
    diDao.updateDatasetLastAttempt(metrics);
  }

  private <M extends CopyDataset> void copyTable(Class entity, Class<M> mapperClass, SqlSession session){
    int count = session.getMapper(mapperClass).copyDataset(datasetKey, newDatasetKey);
    LOG.info("Copied {} {}s", count, entity.getSimpleName());
  }

}
