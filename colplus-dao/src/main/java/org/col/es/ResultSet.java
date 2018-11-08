package org.col.es;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ResultSet<T> {
  
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
