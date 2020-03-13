package life.catalogue.es.response;

/**
 * Models the response coming back from an Elasticsearch query.
 * 
 * @param <T> The type of objects in the response
 * @param <U> The type of object used as a facets container
 * 
 */
public class EsResponse<T, U extends Aggregation> {

  private SearchHits<T> hits;
  private Aggregations<U> aggregations;

  public SearchHits<T> getHits() {
    return hits;
  }

  public Aggregations<U> getAggregations() {
    return aggregations;
  }

}
