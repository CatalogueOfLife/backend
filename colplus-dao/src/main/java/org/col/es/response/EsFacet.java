package org.col.es.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import static org.col.es.response.AggregationLabelProvider.BUCKETS;

/**
 * The data structure containing the facet values and their respective document counts. It contains itself a document count as well, which
 * is the sum total of document counts for the facet.
 */
public class EsFacet {

  @JsonProperty("doc_count")
  private int docCount;

  @JsonProperty(BUCKETS)
  private BucketsContainer bucketsContainer;

  public int getDocCount() {
    return docCount;
  }

  public BucketsContainer getBucketsContainer() {
    return bucketsContainer;
  }

}
