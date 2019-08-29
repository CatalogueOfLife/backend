package org.col.es.dsl;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents what Elasticsearch calls a bucketing aggregation. See
 * https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations.html
 */
public abstract class BucketAggregation extends AbstractAggregation {

  Map<String, Aggregation> aggs;

  public void addNestedAggregation(String label, Aggregation agg) {
    if (aggs == null) {
      aggs = new LinkedHashMap<>();
    }
    aggs.put(label, agg);
  }

  public void setNestedAggregations(Map<String, Aggregation> aggs) {
    this.aggs = aggs;
  }

}
