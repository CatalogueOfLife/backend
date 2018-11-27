package org.col.es.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.col.es.translate.AggregationLabelProvider;

/**
 * An extra layer between the outer "aggregations" field in the ES search response and the facets, arising from the fact that we use a
 * global filter aggregation to shrink the document set over which the facets aggregate.
 */
public class ContextFilterWrapper {

  @JsonProperty("doc_count")
  private int docCount;

  @JsonProperty(AggregationLabelProvider.CONTEXT_FILTER)
  private EsFacets facetsContainer;

  public int getDocCount() {
    return docCount;
  }

  public void setDocCount(int docCount) {
    this.docCount = docCount;
  }

  public EsFacets getFacetsContainer() {
    return facetsContainer;
  }

  public void setFacetsContainer(EsFacets facetsContainer) {
    this.facetsContainer = facetsContainer;
  }

}
