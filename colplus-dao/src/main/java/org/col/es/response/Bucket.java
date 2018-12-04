package org.col.es.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The ES counter part to the FacetValue API class.
 */
public class Bucket {

  private Object key;
  @JsonProperty("doc_count")
  private int docCount;

  public Object getKey() {
    return key;
  }

  public int getDocCount() {
    return docCount;
  }

}
