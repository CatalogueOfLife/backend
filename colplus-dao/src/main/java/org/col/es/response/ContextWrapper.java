package org.col.es.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.col.es.translate.AggregationLabelProvider;

/**
 * This will be the "aggregations" object in the ES query response if we used a ShieldedFacetsTranslator or a SieldedFilterFacetsTranslator.
 * You have to go 2 levels deep to get to the facets.
 */
public class ContextWrapper {

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
