package life.catalogue.es.nu;

import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.es.EsException;
import life.catalogue.es.EsModule;
import life.catalogue.es.EsNameUsage;
import life.catalogue.es.EsUtil;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;

public class NameUsageIndexer implements Consumer<List<NameUsageWrapper>> {
  
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageIndexer.class);

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
  private final Set<TaxGroup> taxGroups;

  NameUsageIndexer(RestClient client, String index) {
    this.client = client;
    this.index = index;
    this.indexHeader = getIndexHeader();
    this.taxGroups = EnumSet.noneOf(TaxGroup.class);
  }

  @Override
  public void accept(List<NameUsageWrapper> batch) {
    index(batch);
  }

  /**
   * Updates the provided documents. The documents are presumed to have their document ID set. These will be used to tell
   * Elasticsearch which documents we want to update. As a side effect, document IDs will be nullified, so make sure you
   * cache them before calling this method if you need them later on.
   * 
   * @param documents
   */
  void update(List<EsNameUsage> documents) {
    buf.setLength(0);
    try {
      for (EsNameUsage doc : documents) {
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
    try {
      for (NameUsageWrapper nuw : batch) {
        buf.append(indexHeader);
        buf.append(EsModule.write(NameUsageWrapperConverter.toDocument(nuw)));
        buf.append("\n");
        if (nuw.getGroup() != null) {
          taxGroups.add(nuw.getGroup());
        }
      }
      sendBatch(batch.size());
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  private void sendBatch(int batchSize) {
    Request request = new Request("POST", "/_bulk/?timeout=5m");
    request.setJsonEntity(buf.toString());
    var resp = EsUtil.executeWithRetry(client, request);
    EsUtil.bulkResponseHasErrors(resp);
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

  public Set<TaxGroup> getTaxGroups() {
    return taxGroups;
  }

  private String getIndexHeader() {
    return String.format("{\"index\":{\"_index\":\"%s\"}}%n", index);
  }

  private String getUpdateHeader(String id) {
    return String.format("{\"update\":{\"_id\":\"%s\",\"_index\":\"%s\"}}%n", id, index);
  }
}
