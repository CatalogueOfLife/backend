package life.catalogue.es.name.index;

import com.google.common.annotations.VisibleForTesting;
import life.catalogue.api.model.Sector;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.common.func.BatchConsumer;
import life.catalogue.dao.NameUsageProcessor;
import life.catalogue.db.mapper.BatchResultHandler;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import life.catalogue.db.mapper.NameUsageWrapperMapper;
import life.catalogue.es.EsConfig;
import life.catalogue.es.EsException;
import life.catalogue.es.EsUtil;
import life.catalogue.es.model.NameUsageDocument;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static life.catalogue.es.EsConfig.ES_INDEX_NAME_USAGE;

public class NameUsageIndexServiceEs implements NameUsageIndexService {

  private static final Logger LOG = LoggerFactory.getLogger(NameUsageIndexServiceEs.class);
  private static final int BATCH_SIZE = 1000;
  private final RestClient client;
  private final EsConfig esConfig;
  private final String index;
  private final SqlSessionFactory factory;
  private final NameUsageProcessor processor;

  public NameUsageIndexServiceEs(RestClient client, EsConfig esConfig, SqlSessionFactory factory) {
    this(client, esConfig, factory, esConfig.indexName(ES_INDEX_NAME_USAGE));
  }

  @VisibleForTesting
  public NameUsageIndexServiceEs(RestClient client, EsConfig esConfig, SqlSessionFactory factory, String index) {
    this.client = client;
    this.index = index;
    this.esConfig = esConfig;
    this.factory = factory;
    this.processor = new NameUsageProcessor(factory);
  }

  @Override
  public void indexDataset(int datasetKey) {
    NameUsageIndexer indexer = new NameUsageIndexer(client, index);
    int tCount, bCount;
    try {
      createOrEmptyIndex(datasetKey);
      try (BatchConsumer<NameUsageWrapper> handler = new BatchConsumer<>(indexer, BATCH_SIZE)) {
        LOG.info("Indexing usages from dataset {}", datasetKey);
        processor.processDataset(datasetKey, handler);
      }
      EsUtil.refreshIndex(client, index);
      tCount = indexer.documentsIndexed();
      indexer.reset();
      try (SqlSession session = factory.openSession(true);
          BatchResultHandler<NameUsageWrapper> handler = new BatchResultHandler<>(indexer, BATCH_SIZE)
      ) {
        NameUsageWrapperMapper mapper = session.getMapper(NameUsageWrapperMapper.class);
        LOG.info("Indexing bare names from dataset {}", datasetKey);
        mapper.processDatasetBareNames(datasetKey, null, handler);
      }
      EsUtil.refreshIndex(client, index);
      bCount = indexer.documentsIndexed();
    } catch (IOException e) {
      throw new EsException(e);
    }
    logDatasetTotals(datasetKey, tCount, bCount);
  }

  @Override
  public void indexSector(Sector s) {
    NameUsageIndexer indexer = new NameUsageIndexer(client, index);
    int tCount, bCount;
    try (SqlSession session = factory.openSession()) {
      deleteSector(s.getKey());
      NameUsageWrapperMapper mapper = session.getMapper(NameUsageWrapperMapper.class);
      
      try (BatchConsumer<NameUsageWrapper> handler = new BatchConsumer<>(indexer, BATCH_SIZE)) {
        LOG.info("Indexing usages from sector {}", s.getKey());
        processor.processSector(s, handler);
      }
      tCount = indexer.documentsIndexed();
      indexer.reset();
      
      try (BatchResultHandler<NameUsageWrapper> handler = new BatchResultHandler<>(indexer, BATCH_SIZE)) {
        LOG.info("Indexing bare names from sector {}", s.getKey());
        mapper.processDatasetBareNames(s.getDatasetKey(), s.getKey(), handler);
      }
      EsUtil.refreshIndex(client, index);
      bCount = indexer.documentsIndexed();
    } catch (IOException e) {
      throw new EsException(e);
    }
    logSectorTotals(s.getKey(), tCount, bCount);
  }

  @Override
  public void deleteSector(int sectorKey) {
    try {
      int cnt = EsUtil.deleteSector(client, index, sectorKey);
      LOG.info("Deleted all {} documents from sector {} from index {}", cnt, sectorKey, index);
      EsUtil.refreshIndex(client, index);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  @Override
  public void sync(int datasetKey, Collection<String> taxonIds) {
    if (!taxonIds.isEmpty()) {
      try {
        String first = taxonIds.iterator().next();
        LOG.info("Syncing {} taxa (first id: {}) from dataset {}", taxonIds.size(), first, datasetKey);
        int deleted = EsUtil.deleteNameUsages(client, index, datasetKey, taxonIds);
        int inserted = indexNameUsages(datasetKey, taxonIds);
        EsUtil.refreshIndex(client, index);
        LOG.info("Finished syncing {} taxa (first id: {}) from dataset {}. Deleted: {}. Inserted: {}.",
            taxonIds.size(),
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
  public void updateClassification(int datasetKey, String rootTaxonId) {
    NameUsageIndexer indexer = new NameUsageIndexer(client, index);
    try (SqlSession session = factory.openSession()) {
      NameUsageWrapperMapper mapper = session.getMapper(NameUsageWrapperMapper.class);
      try (ClassificationUpdater updater = new ClassificationUpdater(indexer, datasetKey)) {
        mapper.processTree(datasetKey, null, rootTaxonId, updater);
      }
      EsUtil.refreshIndex(client, index);
    } catch (IOException e) {
      throw new EsException(e);
    }
    LOG.info("Successfully updated {} name usages", indexer.documentsIndexed());
  }

  @Override
  public void indexAll() {
    NameUsageIndexer indexer = new NameUsageIndexer(client, index);
    int tCount = 0, bCount = 0;
    try {
      EsUtil.deleteIndex(client, index);
      EsUtil.createIndex(client, index, NameUsageDocument.class, esConfig.nameUsage);
      List<Integer> keys;
      try (SqlSession session = factory.openSession(true)) {
        keys = session.getMapper(DatasetMapper.class).keys();
        int allDatasets = keys.size();
        // first check if we have data partitions - otherwise all queries below throw
        DatasetPartitionMapper dpm = session.getMapper(DatasetPartitionMapper.class);
        keys.removeIf(key -> !dpm.exists(key));
        LOG.info("Index {} datasets with data partitions out of all {} datasets", keys.size(), allDatasets);
      }

      int counter = 1;
      for (Integer datasetKey : keys) {
        LOG.info("Index {}/{} dataset {}", counter, keys.size(), datasetKey);
        int tc, bc;
        try (BatchConsumer<NameUsageWrapper> handler = new BatchConsumer<>(indexer, BATCH_SIZE)) {
          LOG.info("Indexing usages from dataset {}", datasetKey);
          processor.processDataset(datasetKey, handler);
        }
        tc = indexer.documentsIndexed();
        indexer.reset();
        try (SqlSession session = factory.openSession(true);
             BatchResultHandler<NameUsageWrapper> handler = new BatchResultHandler<>(indexer, BATCH_SIZE)
        ) {
          NameUsageWrapperMapper mapper = session.getMapper(NameUsageWrapperMapper.class);
          LOG.info("Indexing bare names for dataset {}", datasetKey);
          mapper.processDatasetBareNames(datasetKey, null, handler);
        }
        EsUtil.refreshIndex(client, index);
        bc = indexer.documentsIndexed();
        indexer.reset();
        tCount += tc;
        bCount += bc;
        logDatasetTotals(datasetKey, tc, bc);
      }
      logTotals(keys.size(), tCount, bCount);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  private void createOrEmptyIndex(int datasetKey) throws IOException {
    if (EsUtil.indexExists(client, index)) {
      EsUtil.deleteDataset(client, index, datasetKey);
      EsUtil.refreshIndex(client, index);
    } else {
      EsUtil.createIndex(client, index, NameUsageDocument.class, esConfig.nameUsage);
    }
  }

  /*
   * Indexes documents but does not refresh the index! Must be done by caller.
   */
  private int indexNameUsages(int datasetKey, Collection<String> usageIds) {
    NameUsageIndexer indexer = new NameUsageIndexer(client, index);
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

  private void logDatasetTotals(int datasetKey, int tCount, int bCount) {
    String fmt = "Successfully indexed dataset {}. Index: {}. Usages: {}. Bare names: {}. Total: {}.";
    LOG.info(fmt, datasetKey, index, tCount, bCount, (tCount + bCount));
  }

  private void logSectorTotals(int sectorKey, int tCount, int bCount) {
    String fmt = "Successfully indexed sector {}. Index: {}. Usages: {}. Bare names: {}. Total: {}.";
    LOG.info(fmt, sectorKey, index, tCount, bCount, (tCount + bCount));
  }

  private void logTotals(int numDatasets, int tCount, int bCount) {
    String fmt = "Successfully indexed {} datasets. Index: {}. Usages: {}. Bare names: {}. Total: {}.";
    LOG.info(fmt, index, numDatasets, tCount, bCount, (tCount + bCount));
  }
}
