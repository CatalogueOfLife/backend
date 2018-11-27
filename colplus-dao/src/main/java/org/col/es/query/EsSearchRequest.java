package org.col.es.query;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The main class modeling an ES query. I.e. serializing it to JSON will result in a valid ES query.
 */
public class EsSearchRequest {

  /**
   * Guaranteed to produce an empty search request irrespective of (future) implementation details. Equivalent of {}.
   */
  public static EsSearchRequest emptyRequest() {
    return new EsSearchRequest();
  }

  private Integer size;

  private Integer from;

  private List<SortField> sort;

  private Query query;

  @JsonProperty("aggs")
  private Map<String, Aggregation> aggregations;

  public void addAggregation(String name, Aggregation agg) {
    if (aggregations == null) {
      aggregations = new LinkedHashMap<>();
    }
    aggregations.put(name, agg);
  }

  public Integer getSize() {
    return size;
  }

  public void setSize(Integer size) {
    this.size = size;
  }

  public Integer getFrom() {
    return from;
  }

  public void setFrom(Integer from) {
    this.from = from;
  }

  public List<SortField> getSort() {
    return sort;
  }

  public void setSort(List<SortField> sort) {
    this.sort = sort;
  }

  public Query getQuery() {
    return query;
  }

  public void setQuery(Query query) {
    this.query = query;
  }

  public Map<String, Aggregation> getAggregations() {
    return aggregations;
  }

  public void setAggregations(Map<String, Aggregation> aggregations) {
    this.aggregations = aggregations;
  }

}
