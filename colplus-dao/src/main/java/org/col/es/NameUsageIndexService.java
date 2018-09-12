package org.col.es;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.http.nio.entity.NStringEntity;
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

public class NameUsageIndexService {
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageIndexService.class);
  private final RestClient client;
  private final SqlSessionFactory factory;
  //TODO: make configurable
  private final int BATCH_SIZE = 500;
  final String NDJSON = "application/x-ndjson";
  // object readers & writers are slightly more performant than simple object mappers
  // they also are thread safe!
  private final ObjectWriter writer;

  public NameUsageIndexService(RestClient client, SqlSessionFactory factory) {
    this.client = client;
    this.factory = factory;
    writer = ApiModule.MAPPER.writerFor(NameUsage.class);
  }

  /**
   * Main method to index an entire dataset from postgres into ElasticSearch using the bulk API.
   */
  public void indexDataset(final int datasetKey) throws EsException {
    // drop old index & create new empty one
    createIndex(datasetKey);
    final AtomicInteger counter = new AtomicInteger();
    try (SqlSession session = factory.openSession();
         BatchResultHandler<NameUsage> handler = new BatchResultHandler<NameUsage>(
             batch -> {
               indexBulk(datasetKey, batch);
               counter.addAndGet(batch.size());
             },BATCH_SIZE)
    ) {
      NameUsageMapper mapper = session.getMapper(NameUsageMapper.class);
      mapper.processDataset(datasetKey, handler);

    } catch (Exception e) {
      throw new EsException(e);
    }
    LOG.info("Indexed {} name usages from dataset {} into ES", counter.get(), datasetKey);
  }

  private void createIndex(int datasetKey) {

  }

  static String indexName(int datasetKey) {
    return "nu" + datasetKey;
  }

  private static String indexActionMetaData(int datasetKey) {
    // possible actions are
    return String.format("{ \"index\" : { \"_index\" : \"%s\", \"_type\" : \"_doc\" } }%n",
        indexName(datasetKey));
  }

  private void indexBulk(int datasetKey, List<NameUsage> usages) {
    final String actionMetaData = indexActionMetaData(datasetKey);
    StringBuilder body = new StringBuilder();
    try {
      for (NameUsage nu : usages) {
        body.append(actionMetaData);
        body.append(writer.writeValueAsString(nu));
        body.append("\n");
      }
      Request req = new Request("POST","/_bulk");
      req.setEntity(new NStringEntity(body.toString(), NDJSON));
      client.performRequestAsync(req, new ResponseListener() {
        @Override
        public void onSuccess(Response response) {
          LOG.debug("Successfully indexed batch of {} name usages from dataset {} to ES", usages.size(), datasetKey);
        }

        @Override
        public void onFailure(Exception e) {
          LOG.error("Failed to index batch of {} name usages from dataset {} to ES", usages.size(), datasetKey);
          //TODO: should we really throw or do sth more clever?
          throw new RuntimeException(e);
        }
      });
    } catch (RuntimeException e) {
      throw e;

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    LOG.debug("Submitted batch of {} name usages from dataset {} to ES", usages.size(), datasetKey);
  }

}
