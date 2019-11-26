package org.col.es.name.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.Sector;
import org.col.api.search.NameUsageWrapper;
import org.col.common.func.BatchConsumer;
import org.col.dao.NameUsageProcessor;
import org.col.db.mapper.BatchResultHandler;
import org.col.db.mapper.DatasetMapper;
import org.col.db.mapper.NameUsageWrapperMapper;
import org.col.db.mapper.SectorMapper;
import org.col.es.EsConfig;
import org.col.es.EsException;
import org.col.es.EsUtil;
import org.col.es.model.NameUsageDocument;
import org.col.es.name.NameUsageQueryService;
import org.col.es.query.EsSearchRequest;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import static org.col.es.EsConfig.ES_INDEX_NAME_USAGE;

public class NameUsageIndexServiceEs implements NameUsageIndexService {

  private static final Logger LOG = LoggerFactory.getLogger(NameUsageIndexServiceEs.class);
  private static final int BATCH_SIZE = 4096;
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
      Integer datasetKey = clearSector(session, s.getKey());
      NameUsageWrapperMapper mapper = session.getMapper(NameUsageWrapperMapper.class);
      try (BatchConsumer<NameUsageWrapper> handler = new BatchConsumer<>(indexer, BATCH_SIZE)) {
        LOG.info("Indexing usages from sector {}", s.getKey());
        processor.processSector(s.getDatasetKey(), s.getKey(), s.getTarget().getId(), handler);
      }
      EsUtil.refreshIndex(client, index);
      tCount = indexer.documentsIndexed();
      indexer.reset();
      try (BatchResultHandler<NameUsageWrapper> handler = new BatchResultHandler<>(indexer, BATCH_SIZE)) {
        LOG.info("Indexing bare names from sector {}", s.getKey());
        mapper.processDatasetBareNames(datasetKey, s.getKey(), handler);
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
    try (SqlSession session = factory.openSession()) {
      clearSector(session, sectorKey);
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
        EsUtil.refreshIndex(client, index);
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
      }
      for (Integer datasetKey : keys) {
        int tc, bc;
        try (BatchConsumer<NameUsageWrapper> handler = new BatchConsumer<>(indexer, BATCH_SIZE)) {
          LOG.debug("Indexing usages from dataset {}", datasetKey);
          processor.processDataset(datasetKey, handler);
        }
        EsUtil.refreshIndex(client, index);
        tc = indexer.documentsIndexed();
        indexer.reset();
        try (SqlSession session = factory.openSession(true);
             BatchResultHandler<NameUsageWrapper> handler = new BatchResultHandler<>(indexer, BATCH_SIZE)
        ) {
          NameUsageWrapperMapper mapper = session.getMapper(NameUsageWrapperMapper.class);
          LOG.debug("Indexing bare names for dataset {}", datasetKey);
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

  /**
   * @return datasetKey of the deleted sector
   */
  private Integer clearSector(SqlSession session, Integer sectorKey) throws IOException {
    EsSearchRequest query = EsSearchRequest.emptyRequest();
    query.select("datasetKey").whereEquals("sectorKey", sectorKey).size(1);
    NameUsageQueryService svc = new NameUsageQueryService(index, client);
    List<NameUsageDocument> result = svc.getDocuments(query);
    if (result.size() != 0) {
      int cnt = EsUtil.deleteSector(client, index, sectorKey);
      LOG.debug("Deleted all {} documents from sector {} from index {}", cnt, sectorKey, index);
      return result.get(0).getDatasetKey();
    }
    SectorMapper mapper = session.getMapper(SectorMapper.class);
    Sector sector = mapper.get(sectorKey);
    if (sector == null) {
      throw new IllegalArgumentException("No such sector: " + sectorKey);
    }
    return sector.getDatasetKey();
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
