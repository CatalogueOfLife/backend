package org.col.es.query;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Class modeling a complete Elasticsearch search request. Serializing it to JSON will result in a syntactically valid Elasticsearch query
 * (e.g. which you could execute in Kibana). Instances of this class are produced by a NameSearchRequestTranslator using a NameSearchRequest
 * object as input.
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
