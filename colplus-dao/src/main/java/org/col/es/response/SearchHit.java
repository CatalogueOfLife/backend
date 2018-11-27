package org.col.es.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SearchHit<T> {
  
  @JsonProperty("_id")
  private String id;
  @JsonProperty("_score")
  private float score;
  @JsonProperty("_source")
  private T source;
  
  public String getId() {
    return id;
  }
  
  public float getScore() {
    return score;
  }
  
  public T getSource() {
    return source;
  }
  
}
