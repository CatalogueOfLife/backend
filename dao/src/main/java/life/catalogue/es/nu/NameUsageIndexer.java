package life.catalogue.es.nu;

import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.es.EsException;
import life.catalogue.es.EsNameUsage;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;

public class NameUsageIndexer implements Consumer<List<NameUsageWrapper>> {

  private static final Logger LOG = LoggerFactory.getLogger(NameUsageIndexer.class);

  private final ElasticsearchClient client;
  private final String index;

  private int indexed = 0;
  private final Set<TaxGroup> taxGroups;

  NameUsageIndexer(ElasticsearchClient client, String index) {
    this.client = client;
    this.index = index;
    this.taxGroups = EnumSet.noneOf(TaxGroup.class);
  }

  @Override
  public void accept(List<NameUsageWrapper> batch) {
    index(batch);
  }

  /**
   * Updates the provided documents. The documents are presumed to have their document ID set.
   */
  void update(List<EsNameUsage> documents) {
    try {
      BulkRequest.Builder br = new BulkRequest.Builder();
      for (EsNameUsage doc : documents) {
        String docId = doc.getDocumentId();
        doc.setDocumentId(null);
        br.operations(op -> op.update(u -> u
          .index(index)
          .id(docId)
          .action(a -> a.doc(doc))
        ));
      }
      sendBulk(br.build(), documents.size());
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  private void index(List<NameUsageWrapper> batch) {
    try {
      BulkRequest.Builder br = new BulkRequest.Builder();
      for (NameUsageWrapper nuw : batch) {
        EsNameUsage doc = NameUsageWrapperConverter.toDocument(nuw);
        br.operations(op -> op.index(i -> i
          .index(index)
          .document(doc)
        ));
        if (nuw.getGroup() != null) {
          taxGroups.add(nuw.getGroup());
        }
      }
      sendBulk(br.build(), batch.size());
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  private void sendBulk(BulkRequest request, int batchSize) throws IOException {
    BulkResponse response = client.bulk(request);
    if (response.errors()) {
      for (BulkResponseItem item : response.items()) {
        if (item.error() != null) {
          LOG.error("ES Bulk item error: {}", item.error().reason());
        }
      }
    }
    indexed += batchSize;
  }

  void reset() {
    indexed = 0;
  }

  ElasticsearchClient getEsClient() {
    return client;
  }

  String getIndexName() {
    return index;
  }

  int documentsIndexed() {
    return indexed;
  }

  public Set<TaxGroup> getTaxGroups() {
    return taxGroups;
  }
}
