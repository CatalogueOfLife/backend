package life.catalogue.es.nu;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SimpleNameClassification;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.common.func.BatchConsumer;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.concurrent.ExecutorUtils;
import life.catalogue.concurrent.NamedThreadFactory;
import life.catalogue.dao.DaoUtils;
import life.catalogue.dao.NameUsageProcessor;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import life.catalogue.db.mapper.NameUsageWrapperMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.es.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class NameUsageIndexServiceEs implements NameUsageIndexService {

  private static final Logger LOG = LoggerFactory.getLogger(NameUsageIndexServiceEs.class);
  private static final int BATCH_SIZE = 1000;
  private final RestClient client;
  private final EsConfig esConfig;
  private final SqlSessionFactory factory;
  private final NameUsageProcessor processor;

  @VisibleForTesting
  public NameUsageIndexServiceEs(RestClient client, EsConfig esConfig, SqlSessionFactory factory) {
    this.client = client;
    this.esConfig = esConfig;
    this.factory = factory;
    this.processor = new NameUsageProcessor(factory);
  }

  @Override
  public Stats indexDataset(int datasetKey) {
    return indexDatasetInternal(datasetKey, true);
  }

  @Override
  public BatchConsumer<NameUsageWrapper> buildDatasetIndexingHandler(int datasetKey) {
    LOG.info("Start indexing dataset {}", datasetKey);
    try {
      LOG.info("Remove dataset {} from index", datasetKey);
      createOrEmptyIndex(datasetKey);

      NameUsageIndexer indexer = new NameUsageIndexer(client, esConfig.nameUsage.name);
      return new BatchConsumer<>(indexer, BATCH_SIZE) {
        @Override
        public void close() {
          super.close();
          EsUtil.refreshIndex(client, esConfig.nameUsage.name);
        }
      };

    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  private Stats indexDatasetInternal(int datasetKey, boolean clearIndex) {
    Stats stats = new Stats();
    try (SqlSession lockSession = factory.openSession()) {
      LoggingUtils.setDatasetMDC(datasetKey, getClass());
      LOG.info("Start indexing dataset {}", datasetKey);
      NameUsageIndexer indexer = new NameUsageIndexer(client, esConfig.nameUsage.name);
      // we lock the main dataset tables so they are only accessible by select statements, but not any modifying statements.
      DaoUtils.aquireTableLock(datasetKey, lockSession);
      if (clearIndex) {
        LOG.info("Remove dataset {} from index", datasetKey);
        createOrEmptyIndex(datasetKey);
      }
      try (BatchConsumer<NameUsageWrapper> handler = new BatchConsumer<>(indexer, BATCH_SIZE)) {
        LOG.info("Indexing usages from dataset {}", datasetKey);
        processor.processDataset(datasetKey, handler);
      }
      EsUtil.refreshIndex(client, esConfig.nameUsage.name);
      stats.usages = indexer.documentsIndexed();
      indexer.reset();
      try (SqlSession session = factory.openSession()) {
        LOG.info("Indexing bare names from dataset {}", datasetKey);
        NameUsageWrapperMapper mapper = session.getMapper(NameUsageWrapperMapper.class);
        Cursor<NameUsageWrapper> cursor = mapper.processDatasetBareNames(datasetKey, null);
        Iterables.partition(cursor, BATCH_SIZE).forEach(indexer);
      }
      EsUtil.refreshIndex(client, esConfig.nameUsage.name);
      stats.names = indexer.documentsIndexed();

      LOG.info("Successfully indexed dataset {} into index {}. Usages: {}. Bare names: {}. Total: {}.",
        datasetKey, esConfig.nameUsage.name, stats.usages, stats.names, stats.total());
      return stats;

    } catch (IOException e) {
      throw new EsException(e);

    } finally {
      LoggingUtils.removeDatasetMDC();
    }
  }

  @Override
  public int deleteDataset(int datasetKey) {
    LOG.info("Removing dataset {} from index {}", datasetKey, esConfig.nameUsage.name);
    int cnt = EsUtil.deleteDataset(client, esConfig.nameUsage.name, datasetKey);
    LOG.info("Deleted all {} documents from dataset {} from index {}", cnt, datasetKey, esConfig.nameUsage.name);
    EsUtil.refreshIndex(client, esConfig.nameUsage.name);
    return cnt;
  }

  @Override
  public Stats indexSector(DSID<Integer> sectorKey) {
    Stats stats = new Stats();
    try (SqlSession session = factory.openSession()) {
      Sector s = session.getMapper(SectorMapper.class).get(sectorKey);
      if (s == null) throw NotFoundException.notFound(Sector.class, sectorKey);

      NameUsageIndexer indexer = new NameUsageIndexer(client, esConfig.nameUsage.name);
      deleteSector(s);
      NameUsageWrapperMapper mapper = session.getMapper(NameUsageWrapperMapper.class);

      try (BatchConsumer<NameUsageWrapper> handler = new BatchConsumer<>(indexer, BATCH_SIZE)) {
        LOG.info("Indexing usages from sector {}", s.getKey());
        processor.processSector(s, handler);
      }
      stats.usages = indexer.documentsIndexed();
      indexer.reset();

      LOG.info("Indexing bare names from sector {}", s.getKey());
      Cursor<NameUsageWrapper> cursor = mapper.processDatasetBareNames(s.getDatasetKey(), s.getId());
      Iterables.partition(cursor, BATCH_SIZE).forEach(indexer);

      EsUtil.refreshIndex(client, esConfig.nameUsage.name);
      stats.names = indexer.documentsIndexed();
    }
    LOG.info("Successfully indexed sector {}. Index: {}. Usages: {}. Bare names: {}. Total: {}.",
      sectorKey, esConfig.nameUsage.name, stats.usages, stats.names, stats.total());
    return stats;
  }

  @Override
  public void deleteSector(DSID<Integer> sectorKey) {
    int cnt = EsUtil.deleteSector(client, esConfig.nameUsage.name, sectorKey);
    LOG.info("Deleted all {} documents from sector {} from index {}", cnt, sectorKey, esConfig.nameUsage.name);
    EsUtil.refreshIndex(client, esConfig.nameUsage.name);
  }

  @Override
  public int deleteBareNames(int datasetKey) {
    int cnt = EsUtil.deleteBareNames(client, esConfig.nameUsage.name, datasetKey);
    LOG.info("Deleted all {} bare name documents from dataset {} from index {}", cnt, datasetKey, esConfig.nameUsage.name);
    EsUtil.refreshIndex(client, esConfig.nameUsage.name);
    return cnt;
  }

  @Override
  public void deleteSubtree(DSID<String> root) {
    int cnt = EsUtil.deleteSubtree(client, esConfig.nameUsage.name, root);
    LOG.info("Deleted {} documents for entire subtree of root taxon {} from index {}", cnt, root, esConfig.nameUsage.name);
    EsUtil.refreshIndex(client, esConfig.nameUsage.name);
  }

  @Override
  public void delete(DSID<String> usageId) {
    if (usageId != null) {
      LOG.debug("Delete usage {} from dataset {}", usageId.getId(), usageId.getDatasetKey());
      EsUtil.deleteNameUsages(client, esConfig.nameUsage.name, usageId.getDatasetKey(), List.of(usageId.getId()));
      EsUtil.refreshIndex(client, esConfig.nameUsage.name);
    }
  }

  @Override
  public void update(int datasetKey, Collection<String> usageIds) {
    if (!usageIds.isEmpty()) {
      String first = usageIds.iterator().next();
      LOG.info("Syncing {} taxa  from dataset {}. First id: {}", usageIds.size(), datasetKey, first);
      int deleted = EsUtil.deleteNameUsages(client, esConfig.nameUsage.name, datasetKey, usageIds);
      int inserted = indexNameUsages(datasetKey, usageIds);
      EsUtil.refreshIndex(client, esConfig.nameUsage.name);
      LOG.info("Finished syncing {} taxa (first id: {}) from dataset {}. Deleted: {}. Inserted: {}.",
          usageIds.size(),
          first,
          datasetKey,
          deleted,
          inserted);
    }
  }

  @Override
  public int add(List<NameUsageWrapper> usages) {
    if (!usages.isEmpty()) {
      NameUsageWrapper first = usages.iterator().next();
      LOG.info("Adding {} usages. First: {}", usages.size(), first.getUsage());
      NameUsageIndexer indexer = new NameUsageIndexer(client, esConfig.nameUsage.name);
      indexer.accept(usages);
      return indexer.documentsIndexed();
    }
    return 0;
  }

  @Override
  public void updateClassification(int datasetKey, String rootTaxonId) {
    NameUsageIndexer indexer = new NameUsageIndexer(client, esConfig.nameUsage.name);
    try (SqlSession session = factory.openSession()) {
      NameUsageWrapperMapper mapper = session.getMapper(NameUsageWrapperMapper.class);
      Cursor<SimpleNameClassification> cursor = mapper.processTree(datasetKey, null, rootTaxonId);
      ClassificationUpdater updater = new ClassificationUpdater(indexer, datasetKey);
      Iterables.partition(cursor, BATCH_SIZE).forEach(updater);
      EsUtil.refreshIndex(client, esConfig.nameUsage.name);
    }
    LOG.info("Successfully updated {} name usages", indexer.documentsIndexed());
  }

  @Override
  public int createEmptyIndex() {
    try {
      EsUtil.deleteIndex(client, esConfig.nameUsage);
      return EsUtil.createIndex(client, EsNameUsage.class, esConfig.nameUsage);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  @Override
  public Stats indexAll() {
    createEmptyIndex();

    final Stats total = new Stats();
    List<Integer> keys;
    try (SqlSession session = factory.openSession(true)) {
      keys = session.getMapper(DatasetMapper.class).keys();
      int allDatasets = keys.size();
      // first check if we have data partitions - otherwise all queries below throw
      DatasetPartitionMapper dpm = session.getMapper(DatasetPartitionMapper.class);
      keys.removeIf(key -> !dpm.exists(key));
      LOG.info("Index {} datasets with data partitions out of all {} datasets", keys.size(), allDatasets);
    }

    final AtomicInteger counter = new AtomicInteger(0);
    ExecutorService exec = Executors.newFixedThreadPool(esConfig.indexingThreads, new NamedThreadFactory("ES-Indexer"));
    for (Integer datasetKey : keys) {
      CompletableFuture.supplyAsync(() -> indexDatasetInternal(datasetKey, false), exec)
          .exceptionally(ex -> {
            counter.incrementAndGet();
            LOG.error("Error indexing dataset {}", datasetKey, ex.getCause());
            return null;
          }).thenAccept(st -> {
            total.add(st);
            LOG.info("Indexed {}/{} dataset {}. Total usages {}", counter.incrementAndGet(), keys.size(), datasetKey, total.usages);
          });
    }
    ExecutorUtils.shutdown(exec);

    LOG.info("Successfully indexed all {} datasets. Index: {}. Usages: {}. Bare names: {}. Total: {}.",
      counter, esConfig.nameUsage.name, total.usages, total.names, total.total());
    return total;
  }

  private void createOrEmptyIndex(int datasetKey) throws IOException {
    if (EsUtil.indexExists(client, esConfig.nameUsage.name)) {
      EsUtil.deleteDataset(client, esConfig.nameUsage.name, datasetKey);
      EsUtil.refreshIndex(client, esConfig.nameUsage.name);
    } else {
      EsUtil.createIndex(client, EsNameUsage.class, esConfig.nameUsage);
    }
  }

  /*
   * Indexes documents but does not refresh the index! Must be done by caller.
   */
  private int indexNameUsages(int datasetKey, Collection<String> usageIds) {
    NameUsageIndexer indexer = new NameUsageIndexer(client, esConfig.nameUsage.name);
    try (SqlSession session = factory.openSession()) {
      NameUsageWrapperMapper mapper = session.getMapper(NameUsageWrapperMapper.class);
      List<NameUsageWrapper> usages = usageIds.stream()
          .map(id -> mapper.get(datasetKey, id))
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
      if (usages.isEmpty()) {
        LOG.warn("None of the provided name usage IDs found in dataset {}: {}.", datasetKey, usageIds.stream().collect(joining(", ")));
        return 0;
      }
      if (usages.size() != usageIds.size()) {
        List<String> ids = new ArrayList<>(usageIds);
        ids.removeAll(usages.stream().map(nuw -> nuw.getUsage().getId()).collect(toList()));
        LOG.warn("Some usage IDs not found in dataset {}: {}", datasetKey, ids.stream().collect(joining(", ")));
      }
      indexer.accept(usages);
      return indexer.documentsIndexed();
    }
  }

}
