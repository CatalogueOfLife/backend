package life.catalogue.es.query;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;

import life.catalogue.es.EsException;
import life.catalogue.es.EsModule;

/**
 * Class modeling a complete Elasticsearch search request. Serializing it to JSON produces a syntactically valid
 * Elasticsearch query (e.g. which you could execute in Kibana). Instances of this class are produced by a
 * NameSearchRequestTranslator using a NameSearchRequest object as input.
 */
public class EsSearchRequest {

  /**
   * Guaranteed to produce an empty search request irrespective of (future) implementation details. Equivalent of {}.
   */
  public static EsSearchRequest emptyRequest() {
    return new EsSearchRequest();
  }

  @JsonProperty("_source")
  private Object select;
  private Query query;
  @JsonProperty("aggs")
  private Map<String, Aggregation> aggregations;
  @JsonProperty("search_after")
  private List<String> searchAfter;
  private List<SortField> sort;
  private Integer size;
  private Integer from; 
  @JsonProperty("track_total_hits")
  private Boolean trackTotalHits;

  // Fluent interface

  public EsSearchRequest select(String... fields) {
    if (fields == null || fields.length == 0) {
      select = Boolean.FALSE;
    } else {
      select = CollapsibleList.of(fields);
    }
    return this;
  }

  public EsSearchRequest where(Query query) {
    this.query = query;
    return this;
  }

  public EsSearchRequest whereEquals(String field, Object value) {
    this.query = new TermQuery(field, value);
    return this;
  }

  public EsSearchRequest sortBy(SortField... sortBy) {
    this.sort = sortBy == null ? null : Arrays.asList(sortBy);
    return this;
  }

  public EsSearchRequest size(Integer size) {
    this.size = size;
    return this;
  }

  // Regular getters/setters

  public Object getSelect() {
    return select;
  }

  public void setSelect(List<String> select) {
    this.select = select;
  }

  public void setSelect(boolean allOrNone) {
    this.select = allOrNone;
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

  public List<String> getSearchAfter() {
    return searchAfter;
  }

  public void setSearchAfter(List<String> searchAfter) {
    this.searchAfter = searchAfter;
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

  public Integer getSize() {
    return size;
  }

  public void setSize(Integer size) {
    this.size = size;
  }
  
  public Boolean getTrackTotalHits() {
    return trackTotalHits;
  }

  public void setTrackTotalHits(Boolean trackTotalHits) {
    this.trackTotalHits = trackTotalHits;
  }

  @Override
  public int hashCode() {
    return Objects.hash(aggregations, from, query, select, size, sort);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    EsSearchRequest other = (EsSearchRequest) obj;
    return Objects.equals(aggregations, other.aggregations)
        && Objects.equals(from, other.from)
        && Objects.equals(query, other.query)
        && Objects.equals(select, other.select)
        && Objects.equals(size, other.size)
        && Objects.equals(sort, other.sort);
  }

  public String toString() {
    try {
      return EsModule.write(this);
    } catch (JsonProcessingException e) {
      throw new EsException(e);
    }
  }

}
