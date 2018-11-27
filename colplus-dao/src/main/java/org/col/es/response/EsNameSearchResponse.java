package org.col.es.response;

import org.col.es.model.EsNameUsage;

public class EsNameSearchResponse extends EsSearchResponse<EsNameUsage> {

  private AggregationResult aggregations;

  public AggregationResult getAggregations() {
    return aggregations;
  }

  public void setAggregations(AggregationResult aggregations) {
    this.aggregations = aggregations;
  }
  
}
