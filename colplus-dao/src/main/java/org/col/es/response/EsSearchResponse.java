package org.col.es.response;

/**
 * The outer-most object of an Elasticsearch search response.
 *
 * @param <T> The type of objects in the search response.
 * @param <U> The type of object used as a facets container
 */
public class EsSearchResponse<T, U extends EsFacetsContainer> {

  private int took;
  private SearchHits<T> hits;
  private AggregationResult<U> aggregations;

  public int getTook() {
    return took;
  }

  public SearchHits<T> getHits() {
    return hits;
  }

  public AggregationResult<U> getAggregations() {
    return aggregations;
  }

}
