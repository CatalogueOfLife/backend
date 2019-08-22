package org.col.es.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An extra layer between the "aggregations" object in the ES search response and the facets, arising from the fact that we always use a
 * global filter aggregation in order to shrink the document set over which the facets aggregate, even if it is a no-op "match all" filter.
 */
public class ContextFilterWrapper<T extends EsFacetsContainer> {

  /**
   * The name that we will use to identify this object within the Elasticsearch response object.
   */
  public static final String LABEL = "FILTER";

  @JsonProperty("doc_count")
  private int docCount;

  @JsonProperty(LABEL)
  private T facetsContainer;

  public int getDocCount() {
    return docCount;
  }

  public T getFacetsContainer() {
    return facetsContainer;
  }

}
