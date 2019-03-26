package org.col.es;

import java.io.IOException;
import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.search.NameUsageWrapper;
import org.col.db.mapper.BatchResultHandler;
import org.col.db.mapper.DatasetMapper;
import org.col.db.mapper.NameUsageMapper;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.es.EsConfig.ES_INDEX_NAME_USAGE;

public class NameUsageIndexServiceEs implements NameUsageIndexService {

  private static final Logger LOG = LoggerFactory.getLogger(NameUsageIndexServiceEs.class);

  private final RestClient client;
  private final EsConfig esConfig;
  private final String index;
  private final SqlSessionFactory factory;

  public NameUsageIndexServiceEs(RestClient client, EsConfig esConfig, SqlSessionFactory factory) {
    this.client = client;
    this.index = esConfig.indexName(ES_INDEX_NAME_USAGE);
    this.esConfig = esConfig;
    this.factory = factory;
  }

  @Override
  public void indexDataset(int datasetKey) {
    NameUsageIndexer indexer = new NameUsageIndexer(client, index);
    int tCount, sCount, bCount;
    try (SqlSession session = factory.openSession()) {
      createOrEmptyIndex(index, datasetKey);
      NameUsageMapper mapper = session.getMapper(NameUsageMapper.class);
      try (BatchResultHandler<NameUsageWrapper> handler = new BatchResultHandler<>(indexer, 4096)) {
        LOG.debug("Indexing taxa for dataset {}", datasetKey);
        mapper.processDatasetTaxa(datasetKey, handler);
      }
      EsUtil.refreshIndex(client, index); // Necessary
      tCount = indexer.documentsIndexed();
      indexer.reset();
      try (SynonymResultHandler handler = new SynonymResultHandler(indexer, datasetKey)) {
        LOG.debug("Indexing synonyms for dataset {}", datasetKey);
        mapper.processDatasetSynonyms(datasetKey, handler);
      }
      EsUtil.refreshIndex(client, index); // Optional
      sCount = indexer.documentsIndexed();
      indexer.reset();
      try (BatchResultHandler<NameUsageWrapper> handler = new BatchResultHandler<>(indexer, 4096)) {
        LOG.debug("Indexing bare names for dataset {}", datasetKey);
        mapper.processDatasetBareNames(datasetKey, handler);
      }
      EsUtil.refreshIndex(client, index);
      bCount = indexer.documentsIndexed();
    } catch (IOException e) {
      throw new EsException(e);
    }
    logDatasetTotals(datasetKey, tCount, sCount, bCount);
  }
  
  @Override
  public void indexSector(int sectorKey) {
    int tCount = 0;
    int sCount = 0;
    LOG.warn("NOT indexed sector {}. Index: {}. Taxa: {}. Synonyms: {}. Total: {}.", sectorKey, index, tCount, sCount, (tCount + sCount));
  }
  
  @Override
  public void indexTaxa(int datasetKey, String... taxonIds) {
    LOG.warn("NOT indexed taxa {} from dataset {}", taxonIds, datasetKey);
  }
  
  @Override
  public void indexAll() {
    NameUsageIndexer indexer = new NameUsageIndexer(client, index);
    int tCount = 0, sCount = 0, bCount = 0;
    try (SqlSession session = factory.openSession()) {
      EsUtil.deleteIndex(client, index);
      EsUtil.createIndex(client, index, esConfig.nameUsage);
      List<Integer> keys = session.getMapper(DatasetMapper.class).keys();
      NameUsageMapper mapper = session.getMapper(NameUsageMapper.class);
      for (Integer datasetKey : keys) {
        int tc, sc, bc;
        try (BatchResultHandler<NameUsageWrapper> handler = new BatchResultHandler<>(indexer, 4096)) {
          LOG.debug("Indexing taxa for dataset {}", datasetKey);
          mapper.processDatasetTaxa(datasetKey, handler);
        }
        EsUtil.refreshIndex(client, index);
        tc = indexer.documentsIndexed();
        indexer.reset();
        try (SynonymResultHandler handler = new SynonymResultHandler(indexer, datasetKey)) {
          LOG.debug("Indexing synonyms for dataset {}", datasetKey);
          mapper.processDatasetSynonyms(datasetKey, handler);
        }
        EsUtil.refreshIndex(client, index);
        sc = indexer.documentsIndexed();
        indexer.reset();
        try (BatchResultHandler<NameUsageWrapper> handler = new BatchResultHandler<>(indexer, 4096)) {
          LOG.debug("Indexing bare names for dataset {}", datasetKey);
          mapper.processDatasetBareNames(datasetKey, handler);
        }
        EsUtil.refreshIndex(client, index);
        bc = indexer.documentsIndexed();
        indexer.reset();
        tCount += tc;
        sCount += sc;
        bCount += bc;
        logDatasetTotals(datasetKey, tc, sc, bc);
      }
    } catch (IOException e) {
      throw new EsException(e);
    }
    logTotals(tCount, sCount, bCount);
  }

  private void createOrEmptyIndex(String index, int datasetKey) throws IOException {
    if (EsUtil.indexExists(client, index)) {
      EsUtil.deleteDataset(client, index, datasetKey);
      EsUtil.refreshIndex(client, index);
    } else {
      EsUtil.createIndex(client, index, esConfig.nameUsage);
    }
  }

  private void logDatasetTotals(int datasetKey, int tCount, int sCount, int bCount) {
    String fmt = "Successfully indexed dataset {}. Index: {}. Taxa: {}. Synonyms: {}. Bare names: {}. Total: {}.";
    LOG.info(fmt, datasetKey, index, tCount, sCount, bCount, (tCount + sCount + bCount));
  }

  private void logTotals(int tCount, int sCount, int bCount) {
    String fmt = "Finished indexing datasets. Index: {}. Taxa: {}. Synonyms: {}. Bare names: {}. Total: {}.";
    LOG.info(fmt, index, tCount, sCount, bCount, (tCount + sCount + bCount));
  }
}
