package org.col.es.name.index;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;
import java.util.function.Consumer;

import com.google.common.base.Charsets;

import org.col.api.search.NameUsageWrapper;
import org.col.es.EsException;
import org.col.es.EsModule;
import org.col.es.EsServerVersion;
import org.col.es.EsUtil;
import org.col.es.model.NameUsageDocument;
import org.col.es.name.NameUsageWrapperConverter;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NameUsageIndexer implements Consumer<List<NameUsageWrapper>> {
  
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageIndexer.class);

  // Set to true for extra statistics (make sure it's false in production)
  private static final boolean EXTRA_STATS = false;

  /*
   * The request body. With a batch size of 4096 the request body can grow to about 11 MB for synonyms with zipped
   * payloads, and 20 MB with unzipped payloads. A batch size of 4096 seems about optimal. A batch size of 2048 also
   * performs well, a batch size of 8192 appears to perform slightly worse.
   */
  private final StringBuilder buf = new StringBuilder(1024 * 1024 * 4);

  private final RestClient client;
  private final String index;
  private final String indexHeader;

  private int indexed = 0;

  NameUsageIndexer(RestClient client, String index) {
    this.client = client;
    this.index = index;
    this.indexHeader = getIndexHeader();
  }

  @Override
  public void accept(List<NameUsageWrapper> batch) {
    if (EXTRA_STATS) {
      indexWithExtraStats(batch);
    } else {
      index(batch);
    }
  }

  /**
   * Indexes the provided raw documents. The documents are presumed to have their document ID nullified. Otherwise
   * Elasticsearch will reject them because the document ID isn't part of the document itself (and we use strict typing,
   * so no undeclared fields are allowed).
   * 
   * @param documents
   */
  void indexDocuments(List<NameUsageDocument> documents) {
    buf.setLength(0);
    try {
      for (NameUsageDocument doc : documents) {
        buf.append(indexHeader);
        buf.append(EsModule.write(doc));
        buf.append("\n");
      }
      sendBatch(documents.size());
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  /**
   * Updates the provided documents. The documents are presumed to have their document ID set. These will be used to tell
   * Elasticsearch which documents we want to update. As a side effect, document IDs will be nullified, so make sure you
   * cache them before calling this method if you need them later on.
   * 
   * @param documents
   */
  void update(List<NameUsageDocument> documents) {
    buf.setLength(0);
    try {
      for (NameUsageDocument doc : documents) {
        buf.append(getUpdateHeader(doc.getDocumentId()));
        doc.setDocumentId(null);
        buf.append("{\"doc\":");
        buf.append(EsModule.write(doc));
        buf.append("}\n");
      }
      sendBatch(documents.size());
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  private void index(List<NameUsageWrapper> batch) {
    buf.setLength(0);
    NameUsageWrapperConverter transfer = new NameUsageWrapperConverter();
    try {
      for (NameUsageWrapper nuw : batch) {
        buf.append(indexHeader);
        buf.append(EsModule.write(transfer.toDocument(nuw)));
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
    NameUsageWrapperConverter transfer = new NameUsageWrapperConverter();
    DecimalFormat df = new DecimalFormat("0.0");
    try {
      String json;
      for (NameUsageWrapper nuw : batch) {
        buf.append(indexHeader);
        buf.append(json = EsModule.write(transfer.toDocument(nuw)));
        docSize += json.getBytes(Charsets.UTF_8).length;
        buf.append("\n");
      }
      sendBatch(batch.size());
      double totSize = ((double) docSize / (double) (1024 * 1024));
      double avgSize = ((double) docSize / (double) (batch.size() * 1024));
      String tot = df.format(totSize);
      String avg = df.format(avgSize);
      LOG.info("Average document size: {} KB. Total document size: {} MB.", avg, tot);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  private void sendBatch(int batchSize) throws IOException {
    Request request = new Request("POST", "/_bulk/?timeout=3h");
    request.setJsonEntity(buf.toString());
    EsUtil.executeRequest(client, request);
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

  private String getIndexHeader() {
    String fmt;
    if (EsServerVersion.getInstance(client).is(7)) {
      fmt = "{\"index\":{\"_index\":\"%s\"}}%n";
    } else {
      fmt = "{\"index\":{\"_index\":\"%s\",\"_type\":\"_doc\"}}%n";
    }
    return String.format(fmt, index);
  }

  private String getUpdateHeader(String id) {
    String fmt;
    if (EsServerVersion.getInstance(client).is(7)) {
      fmt = "{\"update\":{\"_id\":\"%s\",\"_index\":\"%s\"}}%n";
    } else {
      fmt = "{\"update\":{\"_id\":\"%s\",\"_index\":\"%s\",\"_type\":\"_doc\"}}%n";
    }
    return String.format(fmt, id, index);
  }
}
