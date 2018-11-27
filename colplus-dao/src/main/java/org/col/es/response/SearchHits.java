package org.col.es.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SearchHits<T> {
  
  private int total;
  @JsonProperty("max_score")
  private float maxScore;
  private List<SearchHit<T>> hits;
  
  public int getTotal() {
    return total;
  }
  
  public float getMaxScore() {
    return maxScore;
  }
  
  public List<SearchHit<T>> getHits() {
    return hits;
  }
  
}
