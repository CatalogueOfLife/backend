package org.col.es;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.elasticsearch.client.ResponseListener;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.es.EsConfig.DEFAULT_TYPE_NAME;
import static org.col.es.EsConfig.NAME_USAGE_BASE;

public class NameUsageIndexService {

  private static final Logger LOG = LoggerFactory.getLogger(NameUsageIndexService.class);

  private final RestClient client;
  private final EsConfig esConfig;
  private final SqlSessionFactory factory;
  /*
   * Asynchronous indexing is problematic if the rest client doesn't stick around long enough for
   * the callbacks to be invoked (as with unit tests).
   */
  private final boolean async;
  private final NameUsageTransfer transfer;
  private final ObjectWriter writer;

  public NameUsageIndexService(RestClient client, EsConfig esConfig, SqlSessionFactory factory) {
    this(client, esConfig, factory, false);
  }

  @VisibleForTesting
  NameUsageIndexService(RestClient client, EsConfig esConfig, SqlSessionFactory factory,
      boolean async) {
    this.client = client;
    this.esConfig = esConfig;
    this.factory = factory;
    this.async = async;
    this.transfer = new NameUsageTransfer();
    this.writer = esConfig.nameUsage.getDocumentWriter();
  }

  /**
   * Main method to index an entire dataset from postgres into ElasticSearch using the bulk API.
   */
  public void indexDataset(final int datasetKey) {
    final String index = NAME_USAGE_BASE;
    final int batchSize = esConfig.nameUsage.batchSize;
    if (EsUtil.indexExists(client, index)) {
      EsUtil.deleteDataset(client, index, datasetKey);
      EsUtil.refreshIndex(client, index);
    } else {
      EsUtil.createIndex(client, index, esConfig.nameUsage);
    }
    final AtomicInteger counter = new AtomicInteger();
    try (SqlSession session = factory.openSession()) {
      NameUsageMapper mapper = session.getMapper(NameUsageMapper.class);
      LOG.debug("Indexing bare names into Elasticsearch");
      mapper.processDatasetBareNames(datasetKey, new BatchResultHandler<>(batch -> {
        indexBulk(index, batch);
        counter.addAndGet(batch.size());
      }, batchSize));
      LOG.debug("Indexing synonyms into Elasticsearch");
      mapper.processDatasetSynonyms(datasetKey, new BatchResultHandler<>(batch -> {
        indexBulk(index, batch);
        counter.addAndGet(batch.size());
      }, batchSize));
      LOG.debug("Indexing taxa into Elasticsearch");
      mapper.processDatasetTaxa(datasetKey, new BatchResultHandler<>(batch -> {
        indexBulk(index, batch);
        counter.addAndGet(batch.size());
      }, batchSize));
    } catch (Exception e) {
      throw new EsException(e);
    } finally {
      EsUtil.refreshIndex(client, index);
    }
    LOG.info("Successfully inserted {} name usages from dataset {} into index {}", counter.get(),
        datasetKey, index);
  }

  @VisibleForTesting
  void indexBulk(String index, List<? extends NameUsageWrapper<?>> usages) {
    String actionMetaData = indexActionMetaData(index);
    StringBuilder body = new StringBuilder();
    try {
      for (NameUsageWrapper<?> nu : usages) {
        body.append(actionMetaData);
        EsNameUsage enu = transfer.toEsDocument(nu);
        body.append(writer.writeValueAsString(enu));
        body.append("\n");
      }
      Request request = new Request("POST", "/_bulk");
      request.setJsonEntity(body.toString());
      if (async) {
        executeAsync(request, index, usages.size());
      } else {
        execute(request, index, usages.size());
      }
    } catch (Exception e) {
      Exceptions.throwRuntime(e);
    }
  }

  private void executeAsync(Request req, String index, int size) {
    client.performRequestAsync(req, new ResponseListener() {

      @Override
      public void onSuccess(Response response) {
        LOG.debug("Successfully inserted {} name usages into index {}", size, index);
      }

      @Override
      public void onFailure(Exception e) {
        // No point in going on
        Exceptions.throwRuntime(e);
      }
    });
  }

  private void execute(Request req, String index, int size) {
    EsUtil.executeRequest(client, req);
    LOG.debug("Successfully inserted {} name usages into index {}", size, index);
  }

  private static String indexActionMetaData(String index) {
    String fmt = "{ \"index\" : { \"_index\" : \"%s\", \"_type\" : \"%s\" } }%n";
    return String.format(fmt, index, DEFAULT_TYPE_NAME);
  }

}
