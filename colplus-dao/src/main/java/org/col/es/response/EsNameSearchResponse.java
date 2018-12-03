package org.col.es.response;

import org.col.es.model.EsNameUsage;

/**
 * A subclass of EsSearchResponse specific for name searches.
 */
public class EsNameSearchResponse extends EsSearchResponse<EsNameUsage> {

  private AggregationResult aggregations;

  public AggregationResult getAggregations() {
    return aggregations;
  }

}
