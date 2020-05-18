package life.catalogue.es.response;

import java.util.Map;

/**
 * Models the response coming back from an Elasticsearch query.
 * 
 * @param <T> The type of objects in the response
 *
 */
public class EsResponse<T> {

  private SearchHits<T> hits;
  private Map<String, Object> aggregations;

  public SearchHits<T> getHits() {
    return hits;
  }

  public Map<String, Object> getAggregations() {
    return aggregations;
  }

}
