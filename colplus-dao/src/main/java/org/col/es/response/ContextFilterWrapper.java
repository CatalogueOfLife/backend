package org.col.es.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.col.es.translate.AggregationLabelProvider;

/**
 * This get wrapped arround the facets within the response if we used a ShieldedFacetsTranslator or a SieldedFilterFacetsTranslator.
 */
public class ContextFilterWrapper {

  @JsonProperty("doc_count")
  private int docCount;

  @JsonProperty(AggregationLabelProvider.CONTEXT_FILTER)
  private FacetsContainer facetsContainer;

  public int getDocCount() {
    return docCount;
  }

  public void setDocCount(int docCount) {
    this.docCount = docCount;
  }

  public FacetsContainer getFacetsContainer() {
    return facetsContainer;
  }

  public void setFacetsContainer(FacetsContainer facetsContainer) {
    this.facetsContainer = facetsContainer;
  }

}
