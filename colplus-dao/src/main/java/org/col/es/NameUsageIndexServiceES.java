package org.col.es;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.search.NameUsageWrapper;
import org.col.common.lang.Exceptions;
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
    final String indexName = ES_INDEX_NAME_USAGE;
    if (EsUtil.indexExists(client, indexName)) {
      EsUtil.deleteDataset(client, indexName, datasetKey);
      EsUtil.refreshIndex(client, indexName);
    } else {
      EsUtil.createIndex(client, indexName, esConfig.nameUsage);
    }
    NameUsageIndexer indexer = new NameUsageIndexer(client, indexName);
    try (SqlSession session = factory.openSession()) {
      NameUsageMapper mapper = session.getMapper(NameUsageMapper.class);
      try (BatchResultHandler<NameUsageWrapper> handler = new BatchResultHandler<NameUsageWrapper>(indexer, 1024)) {
        LOG.debug("Indexing taxa into Elasticsearch");
        mapper.processDatasetTaxa(datasetKey, handler);
      }
      EsUtil.refreshIndex(client, indexName);
      try (SynonymBatchProcessor sbp = new SynonymBatchProcessor(indexer, datasetKey)) {
        try (BatchResultHandler<NameUsageWrapper> handler = new BatchResultHandler<NameUsageWrapper>(sbp, 1024)) {
          LOG.debug("Indexing synonyms into Elasticsearch");
          mapper.processDatasetSynonyms(datasetKey, handler);
        }
      }
      try (BatchResultHandler<NameUsageWrapper> handler = new BatchResultHandler<>(indexer, 1024)) {
        LOG.debug("Indexing bare names into Elasticsearch");
        mapper.processDatasetBareNames(datasetKey, handler);
      }
    } catch (Throwable t) {
      throw Exceptions.asRuntimeException(t);
    } finally {
      EsUtil.refreshIndex(client, indexName);
    }
    LOG.info("Successfully inserted {} name usages from dataset {} into index {}", indexer.documentsIndexed(),
        datasetKey, indexName);
  }

}
