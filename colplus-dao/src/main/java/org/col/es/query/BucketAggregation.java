package org.col.es.query;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class BucketAggregation {

  private final Map<String, Aggregation> aggs = new LinkedHashMap<>();

  public BucketAggregation() {}

  public BucketAggregation(String label, Aggregation agg) {
    addNestedAggregation(label, agg);
  }

  public void addNestedAggregation(String label, Aggregation agg) {
    aggs.put(label, agg);
  }

}
