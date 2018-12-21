package org.col.es;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.annotations.VisibleForTesting;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.search.NameUsageWrapper;
import org.col.common.lang.Exceptions;
import org.col.db.mapper.BatchResultHandler;
import org.col.db.mapper.NameUsageMapper;
import org.col.es.model.EsNameUsage;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.es.EsConfig.DEFAULT_TYPE_NAME;
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
        EsUtil.refreshIndex(client, indexName);
      }
      // try (BatchResultHandler<NameUsageWrapper> handler = new BatchResultHandler<>(batchProcessor, batchSize)) {
      // LOG.debug("Indexing synonyms into Elasticsearch");
      // mapper.processDatasetSynonyms(datasetKey, handler);
      // }
      // try (BatchResultHandler<NameUsageWrapper> handler = new BatchResultHandler<>(indexer, batchSize)) {
      // LOG.debug("Indexing bare names into Elasticsearch");
      // mapper.processDatasetBareNames(datasetKey, handler);
      // }
    } catch (Exception e) {
      throw new EsException(e);
    } finally {
      EsUtil.refreshIndex(client, indexName);
    }
    LOG.info("Successfully inserted {} name usages from dataset {} into index {}", indexer.documentsIndexed(),
        datasetKey, indexName);
  }

  @VisibleForTesting
  public void indexBulk(String index, List<? extends NameUsageWrapper> usages) {
    NameUsageTransfer transfer = new NameUsageTransfer();
    ObjectWriter writer = EsModule.writerFor(EsNameUsage.class);
    String actionMetaData = indexActionMetaData(index);
    StringBuilder body = new StringBuilder(2 << 16);
    try {
      for (NameUsageWrapper nu : usages) {
        body.append(actionMetaData);
        EsNameUsage enu = transfer.toDocument(nu);
        body.append(writer.writeValueAsString(enu));
        body.append("\n");
      }
      Request request = new Request("POST", "/_bulk");
      request.setJsonEntity(body.toString());
      execute(request, index, usages.size());
    } catch (Exception e) {
      Exceptions.throwRuntime(e);
    }
  }

  private void execute(Request req, String index, int size) {
    @SuppressWarnings("unused") // Retrieve errors/warnings from response
    Response response = EsUtil.executeRequest(client, req);
    LOG.debug("Successfully inserted {} name usages into index {}", size, index);
  }

  private static String indexActionMetaData(String index) {
    String fmt = "{ \"index\" : { \"_index\" : \"%s\", \"_type\" : \"%s\" } }%n";
    return String.format(fmt, index, DEFAULT_TYPE_NAME);
  }

}
