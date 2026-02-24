package life.catalogue.es;

import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.search.NameUsageWrapper;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;

/**
 * Base class of search and suggestion services in the es2 package.
 */
public class EsQueryService {

  private static final Logger LOG = LoggerFactory.getLogger(EsQueryService.class);

  protected final String index;
  protected final ElasticsearchClient client;

  public EsQueryService(String indexName, ElasticsearchClient client) {
    this.index = indexName;
    this.client = client;
  }

  /**
   * Returns the raw Elasticsearch documents matching the specified query.
   * Restores usage.sectorMode from the wrapper field after deserialization
   * (usage.sectorMode is @JsonIgnore so it is not in the JSON document).
   */
  public List<NameUsageWrapper> search(SearchRequest searchRequest) {
    try {
      SearchResponse<NameUsageWrapper> response = client.search(searchRequest, NameUsageWrapper.class);
      List<NameUsageWrapper> results = response.hits().hits().stream()
        .map(Hit::source)
        .toList();
      results.forEach(w -> {
        if (w.getSectorMode() != null && w.getUsage() instanceof NameUsageBase nub && nub.getSectorMode() == null) {
          nub.setSectorMode(w.getSectorMode());
        }
      });
      return results;
    } catch (IOException e) {
      throw new EsException(e);
    }
  }
}
