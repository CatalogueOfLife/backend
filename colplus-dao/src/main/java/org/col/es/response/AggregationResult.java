package org.col.es.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The "aggregations" object within the Elasticsearch response.
 */
public class AggregationResult {

  @JsonProperty("doc_count")
  private int docCount;

  @JsonProperty(AggregationLabelProvider.CONTEXT)
  private ContextFilterWrapper contextFilter;

  public int getDocCount() {
    return docCount;
  }

  public ContextFilterWrapper getContextFilter() {
    return contextFilter;
  }

}
