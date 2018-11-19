package org.col.es.query;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The main class modeling an ES query. I.e. serializing it to JSON will result in a valid ES query.
 */
public class EsSearchRequest {

  private Integer size;
  private Integer from;
  private List<SortField> sort;
  private Query query;
  private Map<String, Aggregation> aggs;

  public void addAggregation(String name, Aggregation agg) {
    if (aggs == null) {
      aggs = new LinkedHashMap<>();
    }
    aggs.put(name, agg);
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

  public Map<String, Aggregation> getAggs() {
    return aggs;
  }

  public void setAggs(Map<String, Aggregation> aggs) {
    this.aggs = aggs;
  }

}
