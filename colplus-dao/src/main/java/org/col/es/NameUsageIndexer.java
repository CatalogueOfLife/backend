package org.col.es;

import java.util.List;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectWriter;

import org.col.api.search.NameUsageWrapper;
import org.col.common.lang.Exceptions;
import org.col.es.model.EsNameUsage;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.es.EsConfig.DEFAULT_TYPE_NAME;

public class NameUsageIndexer implements Consumer<List<NameUsageWrapper>> {

  private static final Logger LOG = LoggerFactory.getLogger(NameUsageIndexer.class);

  private final RestClient client;
  private final String index;

  private int indexed = 0;

  public NameUsageIndexer(RestClient client, String index) {
    this.client = client;
    this.index = index;
  }

  @Override
  public void accept(List<NameUsageWrapper> batch) {
    indexBatch(batch);
  }

  public int indexBatch(List<? extends NameUsageWrapper> batch) {
    if (batch.size() == 0) {
      LOG.warn("Ignoring empty batch of name usages");
      return 0;
    }
    NameUsageTransfer transfer = new NameUsageTransfer();
    ObjectWriter writer = EsModule.writerFor(EsNameUsage.class);
    String actionMetaData = metadata();
    StringBuilder body = new StringBuilder(2 << 16);
    try {
      for (NameUsageWrapper nuw : batch) {
        body.append(actionMetaData);
        EsNameUsage enu = transfer.toDocument(nuw);
        body.append(writer.writeValueAsString(enu));
        body.append("\n");
      }
      Request request = new Request("POST", "/_bulk");
      request.setJsonEntity(body.toString());
      @SuppressWarnings("unused")
      Response response = EsUtil.executeRequest(client, request);
      LOG.debug("Successfully inserted {} name usages into index {}", batch.size(), index);
      indexed += batch.size(); // TODO Inspect response and get number of documents actually indexed
      return batch.size();
    } catch (Throwable t) {
      throw Exceptions.asRuntimeException(t);
    }
  }

  /**
   * Returns the number of documents indexed thus far.
   * 
   * @return
   */
  public int documentsIndexed() {
    return indexed;
  }

  private String metadata() {
    String fmt = "{ \"index\" : { \"_index\" : \"%s\", \"_type\" : \"%s\" } }%n";
    return String.format(fmt, index, DEFAULT_TYPE_NAME);
  }
}
