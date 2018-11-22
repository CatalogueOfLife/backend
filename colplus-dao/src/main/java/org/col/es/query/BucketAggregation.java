package org.col.es.query;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents what Elasticsearch calls a bucketing aggregation. Although in theory you can have nested aggregations for any type of
 * aggregation (the query DSL allows it), Elasticsearch itself states that it only makes sense for bucketing aggregation. Therefore you can
 * only add nested aggregations in subclasses of this class (not of AbstractAggregation).
 */
public abstract class BucketAggregation extends AbstractAggregation {

  private Map<String, Aggregation> aggs;

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
