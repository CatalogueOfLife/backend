package org.col.es;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Charsets;

import org.col.api.search.NameUsageWrapper;
import org.col.es.model.EsNameUsage;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.es.EsConfig.DEFAULT_TYPE_NAME;

final class NameUsageIndexer implements Consumer<List<NameUsageWrapper>> {

  private static final Logger LOG = LoggerFactory.getLogger(NameUsageIndexer.class);
  private static final ObjectWriter WRITER = EsModule.writerFor(EsNameUsage.class);

  // Set to true for extra statistics (make sure it's false in production)
  private static final boolean EXTRA_STATS = false;

  /*
   * The request body. With a batch size of 4096 the request body can grow to about 11 MB for synonyms with zipped payloads, and 20 MB with
   * unzipped payloads. A batch size of 4096 seems about optimal. A batch size of 2048 also performs well, a batch size of 8192 appears to
   * perform slightly worse.
   */
  private final StringBuilder buf = new StringBuilder(1024 * 1024 * 4);

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
    if (EXTRA_STATS) {
      indexWithExtraStats(batch);
    } else {
      index(batch);
    }
  }

  void indexRaw(List<EsNameUsage> batch) {
    buf.setLength(0);
    try {
      for (EsNameUsage doc : batch) {
        buf.append(header);
        buf.append(WRITER.writeValueAsString(doc));
        buf.append("\n");
      }
      sendBatch(batch.size());
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  private void index(List<NameUsageWrapper> batch) {
    buf.setLength(0);
    NameUsageTransfer transfer = new NameUsageTransfer();
    try {
      for (NameUsageWrapper nuw : batch) {
        buf.append(header);
        buf.append(WRITER.writeValueAsString(transfer.toDocument(nuw)));
        buf.append("\n");
      }
      sendBatch(batch.size());
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  private void indexWithExtraStats(List<NameUsageWrapper> batch) {
    buf.setLength(0);
    int docSize = 0;
    NameUsageTransfer transfer = new NameUsageTransfer();
    DecimalFormat df = new DecimalFormat("0.0");
    try {
      String json;
      for (NameUsageWrapper nuw : batch) {
        buf.append(header);
        buf.append(json = WRITER.writeValueAsString(transfer.toDocument(nuw)));
        docSize += json.getBytes(Charsets.UTF_8).length;
        buf.append("\n");
      }
      sendBatch(batch.size());
      double reqSize = ((double) buf.toString().getBytes(Charsets.UTF_8).length / (double) (1024 * 1024));
      double totSize = ((double) docSize / (double) (1024 * 1024));
      double avgSize = ((double) docSize / (double) (batch.size() * 1024));
      String req = df.format(reqSize);
      String tot = df.format(totSize);
      String avg = df.format(avgSize);
      LOG.debug("Average document size: {} KB. Total document size: {} MB. Request body size: {} MB.", avg, tot, req);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  private void sendBatch(int batchSize) throws IOException {
    Request request = new Request("POST", "/_bulk");
    request.setJsonEntity(buf.toString());
    EsUtil.executeRequest(client, request);
    LOG.debug("Successfully inserted {} name usages into index {}", batchSize, index);
    indexed += batchSize;
  }

  /**
   * Resets the document counter.
   */
  void reset() {
    indexed = 0;
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
