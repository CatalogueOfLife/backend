package org.col.es.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The data structure within the ES search response representing a single facet (or GROUP BY field in SQL speak). It contains all distinct
 * values (groups) of the field and their document counts. values (groups in SQL speak) and their respective document counts.
 */
public class EsFacet {
  
  /**
   * The name that we will use to identify this object within the Elasticsearch response object.
   */
  public static final String LABEL = "BUCKETS";

  @JsonProperty("doc_count")
  private int docCount;

  @JsonProperty(LABEL)
  private BucketsContainer bucketsContainer;

  public int getDocCount() {
    return docCount;
  }

  public BucketsContainer getBucketsContainer() {
    return bucketsContainer;
  }

}
