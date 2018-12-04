package org.col.es.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An extra layer between the outer "aggregations" field in the ES search response and the facets, arising from the fact that we always use
 * a global filter aggregation to shrink the document set over which the facets aggregate (even if it is a no-op "match all" filter).
 */
public class ContextFilterWrapper {

  @JsonProperty("doc_count")
  private int docCount;

  @JsonProperty(AggregationLabelProvider.CONTEXT_FILTER)
  private EsFacetsContainer facetsContainer;

  public int getDocCount() {
    return docCount;
  }

  public EsFacetsContainer getFacetsContainer() {
    return facetsContainer;
  }

}
