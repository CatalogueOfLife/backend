package org.col.es.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import static org.col.es.translate.AggregationLabelProvider.BUCKETS;

public class EsFacet {

  @JsonProperty("doc_count")
  private int docCount;

  @JsonProperty(BUCKETS)
  private BucketsContainer bucketsContainer;

  public int getDocCount() {
    return docCount;
  }

  public void setDocCount(int docCount) {
    this.docCount = docCount;
  }

  public BucketsContainer getBucketsContainer() {
    return bucketsContainer;
  }

  public void setBuckets(BucketsContainer b) {
    this.bucketsContainer = b;
  }

}
