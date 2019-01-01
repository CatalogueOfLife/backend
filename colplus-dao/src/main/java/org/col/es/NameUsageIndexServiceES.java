package org.col.es;

import java.io.IOException;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.search.NameUsageWrapper;
import org.col.db.mapper.BatchResultHandler;
import org.col.db.mapper.NameUsageMapper;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.es.EsConfig.ES_INDEX_NAME_USAGE;

public class NameUsageIndexServiceES implements NameUsageIndexService {

  private static final Logger LOG = LoggerFactory.getLogger(NameUsageIndexServiceES.class);

  private final RestClient client;
  private final EsConfig esConfig;
  private final SqlSessionFactory factory;

  public NameUsageIndexServiceES(RestClient client, EsConfig esConfig, SqlSessionFactory factory) {
    this.client = client;
    this.esConfig = esConfig;
    this.factory = factory;
  }

  /**
   * Main method to index an entire dataset from postgres into ElasticSearch using the bulk API.
   */
  @Override
  public void indexDataset(int datasetKey) {
    String index = ES_INDEX_NAME_USAGE;
    NameUsageIndexer indexer = new NameUsageIndexer(client, index);
    int tCount, sCount, bCount;
    try (SqlSession session = factory.openSession()) {
      createOrEmptyIndex(datasetKey, index);
      NameUsageMapper mapper = session.getMapper(NameUsageMapper.class);
      try (BatchResultHandler<NameUsageWrapper> handler = new BatchResultHandler<>(indexer, 4096)) {
        LOG.debug("Indexing taxa for dataset {}", datasetKey);
        mapper.processDatasetTaxa(datasetKey, handler);
      }
      tCount = indexer.documentsIndexed();
      EsUtil.refreshIndex(client, index);
      try (SynonymResultHandler handler = new SynonymResultHandler(indexer, datasetKey)) {
        LOG.debug("Indexing synonyms for dataset {}", datasetKey);
        mapper.processDatasetSynonyms(datasetKey, handler);
      }
      sCount = indexer.documentsIndexed() - tCount;
      try (BatchResultHandler<NameUsageWrapper> handler = new BatchResultHandler<>(indexer, 4096)) {
        LOG.debug("Indexing bare names for dataset {}", datasetKey);
        mapper.processDatasetBareNames(datasetKey, handler);
      }
      bCount = indexer.documentsIndexed() - tCount - sCount;
      EsUtil.refreshIndex(client, index);
    } catch (IOException e) {
      throw new EsException(e);
    }
    LOG.info("Successfully indexed {} taxa, {} synonyms and {} bare names (total: {}; dataset: {}; index: {})",
        tCount,
        sCount,
        bCount,
        indexer.documentsIndexed(),
        datasetKey,
        index);
  }

  private void createOrEmptyIndex(int datasetKey, String indexName) throws IOException {
    if (EsUtil.indexExists(client, indexName)) {
      EsUtil.deleteDataset(client, indexName, datasetKey);
      EsUtil.refreshIndex(client, indexName);
    } else {
      EsUtil.createIndex(client, indexName, esConfig.nameUsage);
    }
  }

}
