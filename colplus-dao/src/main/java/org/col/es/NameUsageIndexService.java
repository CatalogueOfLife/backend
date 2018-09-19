package org.col.es;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.annotations.VisibleForTesting;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.jackson.ApiModule;
import org.col.api.model.NameUsage;
import org.col.db.mapper.BatchResultHandler;
import org.col.db.mapper.NameUsageMapper;
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
  private static final ObjectWriter writer = ApiModule.MAPPER.writerFor(NameUsage.class);

  private final RestClient client;
  private final EsConfig esConfig;
  private final SqlSessionFactory factory;

  public NameUsageIndexService(RestClient client, EsConfig esConfig, SqlSessionFactory factory) {
    this.client = client;
    this.esConfig = esConfig;
    this.factory = factory;
  }

  /**
   * Main method to index an entire dataset from postgres into ElasticSearch using the bulk API.
   */
  public void indexDataset(final int datasetKey) throws EsException {
    String index = NAME_USAGE_BASE + datasetKey;
    EsUtil.deleteIndex(client, index);
    EsUtil.createIndex(client, index, esConfig.nameUsage);
    final AtomicInteger counter = new AtomicInteger();
    try (SqlSession session = factory.openSession();
        BatchResultHandler<NameUsage> handler = new BatchResultHandler<NameUsage>(batch -> {
          indexBulk(client, index, batch);
          counter.addAndGet(batch.size());
        }, esConfig.nameUsage.batchSize)) {
      NameUsageMapper mapper = session.getMapper(NameUsageMapper.class);
      mapper.processDataset(datasetKey, handler);
    } catch (Exception e) {
      throw new EsException(e);
    } finally {
      /*
       * NB Even though we refresh here, because indexing is done async, the last batch(es) may not
       * be visible instantaneously!
       */
      EsUtil.refreshIndex(client, index);
    }
    LOG.info("Indexed {} name usages from dataset {} into ES", counter.get(), datasetKey);
  }

  @VisibleForTesting
  static void indexBulk(RestClient client, String index, List<NameUsage> usages) {
    String actionMetaData = indexActionMetaData(index);
    StringBuilder body = new StringBuilder();
    try {
      for (NameUsage nu : usages) {
        body.append(actionMetaData);
        body.append(writer.writeValueAsString(nu));
        body.append("\n");
      }
      Request request = new Request("POST", "/_bulk");
      request.setJsonEntity(body.toString());
      client.performRequestAsync(request, new ResponseListener() {

        @Override
        public void onSuccess(Response response) {
          LOG.debug("Successfully inserted {} name usages into index {}", usages.size(), index);
        }

        @Override
        public void onFailure(Exception e) {
          // No point in going on
          smash(e);
        }
      });
    } catch (Exception e) {
      smash(e);
    }
  }

  private static String indexActionMetaData(String index) {
    String fmt = "{ \"index\" : { \"_index\" : \"%s\", \"_type\" : \"%s\" } }%n";
    return String.format(fmt, index, DEFAULT_TYPE_NAME);
  }

  private static void smash(Exception e) {
    if (e instanceof RuntimeException) {
      throw (RuntimeException) e;
    }
    throw new RuntimeException(e);
  }

}
