package org.col.es.response;

/**
 * The outer shell of an Elasticsearch search response.
 *
 * @param <T> The type of objects in the search response (in our case EsNameUsage)
 */
public class EsSearchResponse<T> {

  private int took;
  private SearchHits<T> hits;
  private AggregationResult aggregations;

  public int getTook() {
    return took;
  }

  public SearchHits<T> getHits() {
    return hits;
  }

  public AggregationResult getAggregations() {
    return aggregations;
  }

  public void setAggregations(AggregationResult aggregations) {
    this.aggregations = aggregations;
  }
}
