package org.col.es.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.col.es.translate.AggregationLabelProvider;

/**
 * This will be the "aggregations" object in the Elasticsearch response.
 */
public class AggregationResult {

  @JsonProperty("doc_count")
  private int docCount;

  @JsonProperty(AggregationLabelProvider.CONTEXT)
  private ContextFilterWrapper contextFilter;

  public int getDocCount() {
    return docCount;
  }

  public void setDocCount(int docCount) {
    this.docCount = docCount;
  }

  public ContextFilterWrapper getContextFilter() {
    return contextFilter;
  }

  public void setContextFilter(ContextFilterWrapper contextFilter) {
    this.contextFilter = contextFilter;
  }

}
