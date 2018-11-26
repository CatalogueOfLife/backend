package org.col.es.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Facet {

  @JsonProperty("doc_count")
  private int docCount;
  /*
   * When translating the NameSearchRequest we uppercase the label (which is what this is) to distinguish it from the (ES-determined)
   * "buckets" property within it. Otherwise the response gets pretty painful to read.
   */
  @JsonProperty("BUCKETS")
  private BucketsContainer buckets;

  public int getDocCount() {
    return docCount;
  }

  public void setDocCount(int docCount) {
    this.docCount = docCount;
  }

  public BucketsContainer getBuckets() {
    return buckets;
  }

  public void setBuckets(BucketsContainer buckets) {
    this.buckets = buckets;
  }

}
