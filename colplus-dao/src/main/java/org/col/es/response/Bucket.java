package org.col.es.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Bucket {

  private Object key;
  @JsonProperty("doc_count")
  private int docCount;

  public Object getKey() {
    return key;
  }

  public void setKey(Object key) {
    this.key = key;
  }

  public int getDocCount() {
    return docCount;
  }

  public void setDocCount(int docCount) {
    this.docCount = docCount;
  }

}
