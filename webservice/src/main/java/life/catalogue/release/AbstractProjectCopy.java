package life.catalogue.release;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetOrigin;
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
public abstract class AbstractProjectCopy implements Runnable {
  protected final Logger LOG = LoggerFactory.getLogger(getClass());
  protected final SqlSessionFactory factory;
  protected final DatasetImportDao diDao;
  protected final NameUsageIndexService indexService;
  protected final int user;
  protected final int datasetKey;
  protected final DatasetImport metrics;
  protected final String actionName;
  protected final int newDatasetKey;
  private final DatasetOrigin newDatasetOrigin;
  protected final boolean mapIds;
  protected DatasetSettings settings;


  public AbstractProjectCopy(String actionName, SqlSessionFactory factory, DatasetImportDao diDao, NameUsageIndexService indexService,
                             int userKey, int datasetKey, Dataset newDataset, boolean mapIds) {
    this.actionName = actionName;
    this.factory = factory;
    this.diDao = diDao;
    this.indexService = indexService;
    this.user = userKey;
    this.mapIds = mapIds;
    this.datasetKey = datasetKey;
    metrics = diDao.createWaiting(datasetKey, this, userKey);
    metrics.setJob(getClass().getSimpleName());
    newDatasetKey = newDataset.getKey();
    newDatasetOrigin = newDataset.getOrigin();
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
      if (newDatasetOrigin == DatasetOrigin.MANAGED) {
        Partitioner.createManagedSequences(factory, newDatasetKey);
      }
      // is an id mapping table needed?
      if (mapIds) {
        LOG.info("Create id mapping tables for project {}", datasetKey);
        try (SqlSession session = factory.openSession(true)) {
          DatasetPartitionMapper dmp = session.getMapper(DatasetPartitionMapper.class);
          DatasetPartitionMapper.IDMAP_TABLES.forEach(t -> dmp.createIdMapTable(t, datasetKey));
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
      Partitioner.indexAndAttach(factory, newDatasetKey);
      Partitioner.createManagedObjects(factory, newDatasetKey);

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
      if (mapIds) {
        LOG.info("Remove id mapping tables for project {}", datasetKey);
        try (SqlSession session = factory.openSession(true)) {
          DatasetPartitionMapper dmp = session.getMapper(DatasetPartitionMapper.class);
          DatasetPartitionMapper.IDMAP_TABLES.forEach(t -> dmp.deleteTable(t, datasetKey));
        } catch (Exception e) {
          // avoid any excpetions as it would bring down the finally block
          LOG.error("Failed to remove id mapping tables for project {}", datasetKey, e);
        }
      }
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

  private void index() {
    LOG.info("Build search index for dataset " + newDatasetKey);
    indexService.indexDataset(newDatasetKey);
  }

  private <M extends CopyDataset> void copyTable(Class entity, Class<M> mapperClass, SqlSession session){
    int count = session.getMapper(mapperClass).copyDataset(datasetKey, newDatasetKey, mapIds);
    LOG.info("Copied {} {}s", count, entity.getSimpleName());
  }

}
