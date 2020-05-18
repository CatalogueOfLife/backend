package life.catalogue.release;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.common.lang.Exceptions;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.Partitioner;
import life.catalogue.db.CRUD;
import life.catalogue.db.Create;
import life.catalogue.db.DatasetProcessable;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.TaxonExtensionMapper;
import life.catalogue.es.NameUsageIndexService;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

import static life.catalogue.common.lang.Exceptions.interruptIfCancelled;

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

  abstract void dataWork() throws Exception;

  void finalWork() throws Exception {
    // dont do nothing - override if needed
  }

  @Override
  public void run() {
    LoggingUtils.setDatasetMDC(datasetKey, getClass());
    try {
      // prepare new tables
      updateState(ImportState.PROCESSING);
      Partitioner.partition(factory, newDatasetKey);

      dataWork();

      // build indices and attach partition
      Partitioner.indexAndAttach(factory, newDatasetKey);
      // create metrics
      updateState(ImportState.BUILDING_METRICS);
      metrics();
      try {
        // ES index
        updateState(ImportState.INDEXING);
        index();
      } catch (RuntimeException e) {
        // allow indexing to fail - sth we can do afterwards again
        LOG.error("Error indexing new dataset {} into ES. Source dataset={}", newDatasetKey, datasetKey, e);
      }

      finalWork();

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

  void updateState(ImportState state) {
    interruptIfCancelled();
    metrics.setState(state);
    diDao.update(metrics);
  }

  protected void index() {
    LOG.info("Build search index for dataset " + newDatasetKey);
    indexService.indexDataset(newDatasetKey);
  }

  protected void metrics() {
    LOG.info("Build import metrics for dataset " + newDatasetKey);
    diDao.updateMetrics(metrics);
    diDao.update(metrics);
    diDao.updateDatasetLastAttempt(metrics);
  }

  protected <V, M extends Create<V> & DatasetProcessable<V>> void copyTable(Class<M> mapperClass, Class<V> entity, Consumer<V> updater) {
    TableCopyHandler<V,M> handler = new TableCopyHandler<>(factory, entity.getSimpleName(), mapperClass, updater);
    copyTableInternal(mapperClass, entity, handler);
  }

  protected <V extends DatasetScopedEntity<Integer>, M extends CRUD<DSID<Integer>, V> & DatasetProcessable<V>> Int2IntMap copyTableWithKeyMap(Class<M> mapperClass, Class<V> entity, Consumer<V> updater) {
    TableCopyHandlerWithKeyMap<V,M> handler = new TableCopyHandlerWithKeyMap<>(factory, entity.getSimpleName(), mapperClass, updater);
    copyTableInternal(mapperClass, entity, handler);
    return handler.getKeyMap();
  }

  private <V, M extends Create<V> & DatasetProcessable<V>> void copyTableInternal(Class<M> mapperClass, Class<V> entity, TableCopyHandlerBase<V> handler) {
    interruptIfCancelled();
    try (SqlSession session = factory.openSession(false)) {
      LOG.info("Copy all {}", entity.getSimpleName());
      DatasetProcessable<V> mapper = session.getMapper(mapperClass);
      mapper.processDataset(datasetKey).forEach(handler);
      LOG.info("Copied {} {}", handler.getCounter(), entity.getSimpleName());
    } finally {
      handler.close();
    }
  }

  protected <V extends DatasetScopedEntity<Integer>> void copyExtTable(Class<? extends TaxonExtensionMapper<V>> mapperClass, Class<V> entity, Consumer<TaxonExtension<V>> updater) {
    interruptIfCancelled();
    try (SqlSession session = factory.openSession(false);
         ExtTableCopyHandler<V> handler = new ExtTableCopyHandler<V>(factory, entity.getSimpleName(), mapperClass, updater)
    ) {
      LOG.info("Copy all {}", entity.getSimpleName());
      TaxonExtensionMapper<V> mapper = session.getMapper(mapperClass);
      mapper.processDataset(datasetKey).forEach(handler);
      LOG.info("Copied {} {}", handler.getCounter(), entity.getSimpleName());
    }
  }
}
