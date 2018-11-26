package org.col.es.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Bucket {

  private String key;
  @JsonProperty("doc_count")
  private int docCount;

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public int getDocCount() {
    return docCount;
  }

  public void setDocCount(int docCount) {
    this.docCount = docCount;
  }

}
