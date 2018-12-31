package org.col.es;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectWriter;

import org.col.api.search.NameUsageWrapper;
import org.col.es.model.EsNameUsage;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.es.EsConfig.DEFAULT_TYPE_NAME;

class NameUsageIndexer implements Consumer<List<NameUsageWrapper>> {

  private static final Logger LOG = LoggerFactory.getLogger(NameUsageIndexer.class);
  private static final ObjectWriter writer = EsModule.writerFor(EsNameUsage.class);

  private final StringBuilder buf = new StringBuilder(1024 * 1024);

  private final RestClient client;
  private final String index;
  private final String header;

  private int indexed = 0;

  NameUsageIndexer(RestClient client, String index) {
    this.client = client;
    this.index = index;
    this.header = getHeader();
  }

  @Override
  public void accept(List<NameUsageWrapper> batch) {
    if (batch.size() == 0) {
      LOG.info("Ignoring empty batch of name usages");
      return;
    }
    buf.setLength(0);
    NameUsageTransfer transfer = new NameUsageTransfer();
    try {
      for (NameUsageWrapper nuw : batch) {
        buf.append(header);
        EsNameUsage enu = transfer.toDocument(nuw);
        buf.append(writer.writeValueAsString(enu));
        buf.append("\n");
      }
      Request request = new Request("POST", "/_bulk");
      request.setJsonEntity(buf.toString());
      @SuppressWarnings("unused")
      Response response = EsUtil.executeRequest(client, request);
      LOG.debug("Successfully inserted {} name usages into index {}", batch.size(), index);
      indexed += batch.size(); // TODO Inspect response and get number of documents actually indexed
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  /**
   * Returns the Elasticsearc client used to connect to the Elasticsearch server.
   * 
   * @return
   */
  RestClient getEsClient() {
    return client;
  }

  /**
   * Returns the name of the index that this NameUsageIndexer inserts into.
   * 
   * @return
   */
  String getIndexName() {
    return index;
  }

  /**
   * Returns the number of documents indexed thus far.
   * 
   * @return
   */
  int documentsIndexed() {
    return indexed;
  }

  private String getHeader() {
    String fmt = "{\"index\":{\"_index\":\"%s\",\"_type\":\"%s\"}}%n";
    return String.format(fmt, index, DEFAULT_TYPE_NAME);
  }
}
