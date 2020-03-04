package life.catalogue.es.name.index;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SimpleNameClassification;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.common.concurrent.ExecutorUtils;
import life.catalogue.common.concurrent.NamedThreadFactory;
import life.catalogue.common.func.BatchConsumer;
import life.catalogue.dao.NameUsageProcessor;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import life.catalogue.db.mapper.NameUsageWrapperMapper;
import life.catalogue.es.EsConfig;
import life.catalogue.es.EsException;
import life.catalogue.es.EsUtil;
import life.catalogue.es.model.NameUsageDocument;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  class Stats {
    int usages;
    int names;

    int total(){
      return usages+names;
    }

    void add(Stats other) {
      usages += other.usages;
      names += other.names;
    }
  }

  @Override
  public void indexDataset(int datasetKey) {
    indexDatasetInternal(datasetKey, true);
  }

  private Stats indexDatasetInternal(int datasetKey, boolean clearIndex) {
    NameUsageIndexer indexer = new NameUsageIndexer(client, esConfig.nameUsage.name);
    Stats stats = new Stats();
    try {
      LOG.info("Start indexing dataset {}", datasetKey);
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
      try (SqlSession session = factory.openSession(true)) {
        LOG.info("Indexing bare names from dataset {}", datasetKey);
        NameUsageWrapperMapper mapper = session.getMapper(NameUsageWrapperMapper.class);
        Cursor<NameUsageWrapper> cursor = mapper.processDatasetBareNames(datasetKey, null);
        Iterables.partition(cursor, BATCH_SIZE).forEach(indexer);
      }
      EsUtil.refreshIndex(client, esConfig.nameUsage.name);
      stats.names = indexer.documentsIndexed();
    } catch (IOException e) {
      throw new EsException(e);
    }
    LOG.info("Successfully indexed dataset {} into index {}. Usages: {}. Bare names: {}. Total: {}.",
            datasetKey, esConfig.nameUsage.name, stats.usages, stats.names, stats.total()
    );
    return stats;
  }

  @Override
  public int deleteDataset(int datasetKey) {
    try {
      LOG.info("Removing dataset {} from index {}", datasetKey, esConfig.nameUsage.name);
      int cnt = EsUtil.deleteDataset(client, esConfig.nameUsage.name, datasetKey);
      LOG.info("Deleted all {} documents from dataset {} from index {}", cnt, datasetKey, esConfig.nameUsage.name);
      EsUtil.refreshIndex(client, esConfig.nameUsage.name);
      return cnt;
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  @Override
  public void indexSector(Sector s) {
    NameUsageIndexer indexer = new NameUsageIndexer(client, esConfig.nameUsage.name);
    Stats stats = new Stats();
    try (SqlSession session = factory.openSession()) {
      deleteSector(s.getKey());
      NameUsageWrapperMapper mapper = session.getMapper(NameUsageWrapperMapper.class);

      try (BatchConsumer<NameUsageWrapper> handler = new BatchConsumer<>(indexer, BATCH_SIZE)) {
        LOG.info("Indexing usages from sector {}", s.getKey());
        processor.processSector(s, handler);
      }
      stats.usages = indexer.documentsIndexed();
      indexer.reset();

      LOG.info("Indexing bare names from sector {}", s.getKey());
      Cursor<NameUsageWrapper> cursor = mapper.processDatasetBareNames(s.getDatasetKey(), s.getKey());
      Iterables.partition(cursor, BATCH_SIZE).forEach(indexer);

      EsUtil.refreshIndex(client, esConfig.nameUsage.name);
      stats.names = indexer.documentsIndexed();
    } catch (IOException e) {
      throw new EsException(e);
    }
    LOG.info("Successfully indexed sector {}. Index: {}. Usages: {}. Bare names: {}. Total: {}.",
            s.getKey(), esConfig.nameUsage.name, stats.usages, stats.names, stats.total()
    );
  }

  @Override
  public void deleteSector(int sectorKey) {
    try {
      int cnt = EsUtil.deleteSector(client, esConfig.nameUsage.name, sectorKey);
      LOG.info("Deleted all {} documents from sector {} from index {}", cnt, sectorKey, esConfig.nameUsage.name);
      EsUtil.refreshIndex(client, esConfig.nameUsage.name);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  @Override
  public void deleteSubtree(DSID<String> root) {
    try {
      int cnt = EsUtil.deleteSubtree(client, esConfig.nameUsage.name, root);
      LOG.info("Deleted {} documents for entire subtree of root taxon {} from index {}", cnt, root, esConfig.nameUsage.name);
      EsUtil.refreshIndex(client, esConfig.nameUsage.name);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  @Override
  public void delete(DSID<String> usageId) {
    if (usageId != null) {
      try {
        LOG.debug("Delete usage {} from dataset {}", usageId.getId(), usageId.getDatasetKey());
        EsUtil.deleteNameUsages(client, esConfig.nameUsage.name, usageId.getDatasetKey(), List.of(usageId.getId()));
        EsUtil.refreshIndex(client, esConfig.nameUsage.name);
      } catch (IOException e) {
        throw new EsException(e);
      }
    }
  }

  @Override
  public void update(int datasetKey, Collection<String> usageIds) {
    if (!usageIds.isEmpty()) {
      try {
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
      } catch (IOException e) {
        throw new EsException(e);
      }
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
    } catch (IOException e) {
      throw new EsException(e);
    }
    LOG.info("Successfully updated {} name usages", indexer.documentsIndexed());
  }

  @Override
  public void indexAll() {
    final Stats total = new Stats();
    try {
      EsUtil.deleteIndex(client, esConfig.nameUsage);
      EsUtil.createIndex(client, NameUsageDocument.class, esConfig.nameUsage);
      List<Integer> keys;
      try (SqlSession session = factory.openSession(true)) {
        keys = session.getMapper(DatasetMapper.class).keys();
        int allDatasets = keys.size();
        // first check if we have data partitions - otherwise all queries below throw
        DatasetPartitionMapper dpm = session.getMapper(DatasetPartitionMapper.class);
        keys.removeIf(key -> !dpm.exists(key));
        LOG.info("Index {} datasets with data partitions out of all {} datasets", keys.size(), allDatasets);
      }

      final AtomicInteger counter = new AtomicInteger(1);
      ExecutorService exec = Executors.newFixedThreadPool(esConfig.indexingThreads, new NamedThreadFactory("ES-Indexer"));
      for (Integer datasetKey : keys) {
        CompletableFuture.supplyAsync(() -> indexDatasetInternal(datasetKey, false), exec)
            .exceptionally(ex -> {
              counter.incrementAndGet();
              LOG.error("Error indexing dataset {}", datasetKey, ex.getCause());
              return null;
            }).thenAccept( st -> {
              total.add(st);
              LOG.info("Indexed {}/{} dataset {}. Total usages {}", counter.incrementAndGet(), keys.size(), datasetKey, total.usages);
            });
      }
      ExecutorUtils.shutdown(exec);

      LOG.info("Successfully indexed all {} datasets. Index: {}. Usages: {}. Bare names: {}. Total: {}.",
              counter, esConfig.nameUsage.name, total.usages, total.names, total.total()
      );
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  private void createOrEmptyIndex(int datasetKey) throws IOException {
    if (EsUtil.indexExists(client, esConfig.nameUsage.name)) {
      EsUtil.deleteDataset(client, esConfig.nameUsage.name, datasetKey);
      EsUtil.refreshIndex(client, esConfig.nameUsage.name);
    } else {
      EsUtil.createIndex(client, NameUsageDocument.class, esConfig.nameUsage);
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
