package org.col.es.query;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class BucketAggregation extends AbstractAggregation {

  private Map<String, Aggregation> aggs;

  public void addNestedAggregation(String label, Aggregation agg) {
    if (aggs == null) {
      aggs = new LinkedHashMap<>();
    }
    aggs.put(label, agg);
  }

}
