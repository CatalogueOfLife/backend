package org.col.es.response;

/**
 * The response object for an Elasticsearch query with aggregations.
 *
 * @param <T> The type of objects in the response
 * @param <U> The type of object used as a facets container
 */
public class EsAggregationQueryResponse<T, U extends EsFacetsContainer> extends EsQueryResponse<T> {

  private AggregationResult<U> aggregations;

  public AggregationResult<U> getAggregations() {
    return aggregations;
  }

}
