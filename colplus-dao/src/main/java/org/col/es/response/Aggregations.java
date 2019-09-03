package org.col.es.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The "aggregations" object within the Elasticsearch response. Note that there are several layers between this object
 * and the actual aggregations (a.k.a. facets).
 */
public class Aggregations<T extends Aggregation> {

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
