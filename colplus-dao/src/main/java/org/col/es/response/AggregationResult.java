package org.col.es.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The "aggregations" object within the Elasticsearch response.
 */
public class AggregationResult<T extends EsFacetsContainer> {

  /**
   * The name that we will use to identify this object within the Elasticsearch response object.
   */
  public static final String LABEL = "CONTEXT";

  @JsonProperty("doc_count")
  private int docCount;

  @JsonProperty(LABEL)
  private ContextFilterWrapper<T> contextFilter;

  public int getDocCount() {
    return docCount;
  }

  public ContextFilterWrapper<T> getContextFilter() {
    return contextFilter;
  }

}
