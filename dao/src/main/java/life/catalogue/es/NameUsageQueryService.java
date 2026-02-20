package life.catalogue.es;

import life.catalogue.es.search.NameUsageSearchServiceEs;
import life.catalogue.es.suggest.NameUsageSuggestionServiceEs;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;

/**
 * Base class of {@link NameUsageSearchServiceEs} and {@link NameUsageSuggestionServiceEs}.
 */
public class NameUsageQueryService {

  private static final Logger LOG = LoggerFactory.getLogger(NameUsageQueryService.class);

  protected final String index;
  protected final ElasticsearchClient client;

  public NameUsageQueryService(String indexName, ElasticsearchClient client) {
    this.index = indexName;
    this.client = client;
  }

  /**
   * Returns the raw Elasticsearch documents matching the specified query.
   */
  public List<EsNameUsage> getDocuments(SearchRequest searchRequest) {
    try {
      SearchResponse<EsNameUsage> response = client.search(searchRequest, EsNameUsage.class);
      return response.hits().hits().stream()
        .map(Hit::source)
        .toList();
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  /**
   * Returns the raw Elasticsearch documents with their internal document IDs set.
   */
  public List<EsNameUsage> getDocumentsWithDocId(SearchRequest searchRequest) {
    try {
      SearchResponse<EsNameUsage> response = client.search(searchRequest, EsNameUsage.class);
      return response.hits().hits().stream()
        .map(hit -> {
          EsNameUsage doc = hit.source();
          if (doc != null) {
            doc.setDocumentId(hit.id());
          }
          return doc;
        })
        .toList();
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  protected SearchResponse<EsNameUsage> executeSearchRequest(String index, SearchRequest searchRequest) throws IOException {
    if (LOG.isTraceEnabled()) {
      LOG.trace("Executing search on index: {}", index);
    }
    return client.search(searchRequest, EsNameUsage.class);
  }

}
