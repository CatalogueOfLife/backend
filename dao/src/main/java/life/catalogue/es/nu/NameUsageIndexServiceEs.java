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
import life.catalogue.dao.NameUsageProcessor;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameUsageWrapperMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.es.*;
import life.catalogue.matching.TaxGroupAnalyzer;

import java.io.File;
import java.io.IOException;
import java.util.*;
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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class NameUsageIndexServiceEs implements NameUsageIndexService {

  private static final Logger LOG = LoggerFactory.getLogger(NameUsageIndexServiceEs.class);
  private static final int BATCH_SIZE = 1000;
  private final RestClient client;
  private final EsConfig esConfig;
  private final SqlSessionFactory factory;
  private final NameUsageProcessor processor;
  private final TaxGroupAnalyzer groupAnalyzer = new TaxGroupAnalyzer();

  public NameUsageIndexServiceEs(RestClient client, EsConfig esConfig, File tmpDir, SqlSessionFactory factory) {
    this.client = client;
    this.esConfig = esConfig;
    this.factory = factory;
    this.processor = new NameUsageProcessor(factory, tmpDir);
  }

  @Override
  public Stats indexDataset(int datasetKey) {
    return indexDatasetInternal(datasetKey, true);
  }

  /**
   * Parallelize indexing of several datasets.
   */
  @Override
  public Stats indexDatasets(List<Integer> keys) {
    LOG.info("Index {} datasets", keys.size());
    final Stats total = new Stats();

    final AtomicInteger counter = new AtomicInteger(0);
    ExecutorService exec = Executors.newFixedThreadPool(esConfig.indexingThreads, new NamedThreadFactory("ES-Indexer"));
    for (Integer datasetKey : keys) {
      CompletableFuture.supplyAsync(() -> indexDatasetInternal(datasetKey, true), exec)
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

    LOG.info("Successfully indexed {} datasets. Index: {}. Usages: {}. Bare names: {}. Total: {}.",
      counter, esConfig.nameUsage.name, total.usages, total.names, total.total());
    return total;
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

  private void index(Cursor<NameUsageWrapper> cursor, NameUsageIndexer indexer) throws IOException {
    try (BatchConsumer<NameUsageWrapper> handler = new BatchConsumer<>(indexer, BATCH_SIZE)) {
      PgUtils.consume(
        () -> cursor,
        handler
      );
    }
  }

  private Stats indexDatasetInternal(int datasetKey, boolean clearIndex) {
    try {
      LoggingUtils.setDatasetMDC(datasetKey, getClass());
      Stats stats = new Stats();
      NameUsageIndexer indexer = new NameUsageIndexer(client, esConfig.nameUsage.name);
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
        index(mapper.processDatasetBareNames(datasetKey, null), indexer);
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
    try {
      LoggingUtils.setDatasetMDC(datasetKey, getClass());
      LOG.info("Removing dataset {} from index {}", datasetKey, esConfig.nameUsage.name);
      int cnt = EsUtil.deleteDataset(client, esConfig.nameUsage.name, datasetKey);
      LOG.info("Deleted all {} documents from dataset {} from index {}", cnt, datasetKey, esConfig.nameUsage.name);
      EsUtil.refreshIndex(client, esConfig.nameUsage.name);
      return cnt;

    } finally {
      LoggingUtils.removeDatasetMDC();
    }
  }

  @Override
  public Stats indexSector(DSID<Integer> sectorKey) {
    Stats stats = new Stats();
    try (SqlSession session = factory.openSession()) {
      LoggingUtils.setSectorMDC(sectorKey);
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
      index(mapper.processDatasetBareNames(s.getDatasetKey(), s.getId()), indexer);

      EsUtil.refreshIndex(client, esConfig.nameUsage.name);
      stats.names = indexer.documentsIndexed();

      LOG.info("Successfully indexed sector {}. Index: {}. Usages: {}. Bare names: {}. Total: {}.",
        sectorKey, esConfig.nameUsage.name, stats.usages, stats.names, stats.total());
      return stats;

    } catch (IOException e) {
      throw new EsException(e);

    } finally {
      LoggingUtils.removeSectorMDC();
    }
  }

  @Override
  public void deleteSector(DSID<Integer> sectorKey) {
    try {
      LoggingUtils.setSectorMDC(sectorKey);
      int cnt = EsUtil.deleteSector(client, esConfig.nameUsage.name, sectorKey);
      LOG.info("Deleted all {} documents from sector {} from index {}", cnt, sectorKey, esConfig.nameUsage.name);
      EsUtil.refreshIndex(client, esConfig.nameUsage.name);

    } finally {
      LoggingUtils.removeSectorMDC();
    }
  }

  @Override
  public int deleteBareNames(int datasetKey) {
    try {
      LoggingUtils.setDatasetMDC(datasetKey);
      int cnt = EsUtil.deleteBareNames(client, esConfig.nameUsage.name, datasetKey);
      LOG.info("Deleted all {} bare name documents from dataset {} from index {}", cnt, datasetKey, esConfig.nameUsage.name);
      EsUtil.refreshIndex(client, esConfig.nameUsage.name);
      return cnt;
    } finally {
      LoggingUtils.removeDatasetMDC();
    }
  }

  @Override
  public void deleteSubtree(DSID<String> root, boolean keepRoot) {
    int cnt = EsUtil.deleteSubtree(client, esConfig.nameUsage.name, root, keepRoot);
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
      LOG.info("Syncing {} taxa from dataset {}. First id: {}", usageIds.size(), datasetKey, first);
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
      final ClassificationUpdater updater = new ClassificationUpdater(indexer, datasetKey);
      try (BatchConsumer<SimpleNameClassification> batchUpdater = new BatchConsumer<>(updater, BATCH_SIZE)) {
        NameUsageWrapperMapper mapper = session.getMapper(NameUsageWrapperMapper.class);
        PgUtils.consume(() -> mapper.processTree(datasetKey, null, rootTaxonId), batchUpdater);
      }

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
  public Stats indexAll(int ... excludedDatasetKeys) {
    createEmptyIndex();

    final List<Integer> keys;
    try (SqlSession session = factory.openSession(true)) {
      keys = session.getMapper(DatasetMapper.class).keys();
      int allDatasets = keys.size();
      if (excludedDatasetKeys != null) {
        for (Integer dk : excludedDatasetKeys) {
          keys.remove(dk);
        }
      }
      LOG.info("Index {} datasets out of all {} datasets", keys.size(), allDatasets);
    }
    return indexDatasets(keys);
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
    var usages = buildNameUsageWrappers(datasetKey, usageIds);
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

  public List<NameUsageWrapper> buildNameUsageWrappers(int datasetKey, Collection<String> usageIds) {
    try (SqlSession session = factory.openSession()) {
      NameUsageWrapperMapper mapper = session.getMapper(NameUsageWrapperMapper.class);
      var dm = session.getMapper(DatasetMapper.class);
      var sm = session.getMapper(SectorMapper.class);

      final UUID publisher = session.getMapper(DatasetMapper.class).getPublisherKey(datasetKey);
      final LoadingCache<Integer, NameUsageProcessor.SectorProps> sectors = Caffeine.newBuilder()
        .maximumSize(1000)
        .build(id -> loadSectorProp(datasetKey, id, sm, dm));

      // the following code populates individual name usage wrappers
      // Important! This always needs to match the logic for the bulk dataset/sector handling in NameUsageProcessor.processTree !!!
      List<NameUsageWrapper> usages = usageIds.stream()
        .map(id -> {
          // this already contains the classification, issues, decisions & secondary sources
          var nuw = mapper.get(datasetKey, id);
          if (nuw != null) {
            nuw.setPublisherKey(publisher);
            if (nuw.getUsage().getSectorKey() != null) {
              NameUsageProcessor.addUsageSectorData(nuw, sectors.get(nuw.getUsage().getSectorKey()));
            }
            if (nuw.getUsage().getName() != null && nuw.getUsage().getName().getSectorKey() != null) {
              NameUsageProcessor.addNameSectorData(nuw, sectors.get(nuw.getUsage().getName().getSectorKey()));
            }
            // group
            nuw.setGroup(groupAnalyzer.analyze(nuw.getUsage().toSimpleNameLink(), nuw.getClassification()));
          }
          return nuw;
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
      return usages;
    }
  }
  private NameUsageProcessor.SectorProps loadSectorProp(int datasetKey, Integer sectorKey, SectorMapper sm, DatasetMapper dm) {
    return new NameUsageProcessor.SectorProps(sm.get(DSID.of(datasetKey, sectorKey)), dm);
  }
}
