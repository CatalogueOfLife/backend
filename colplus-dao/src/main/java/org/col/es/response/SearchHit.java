package org.col.es.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SearchHit<T> {

  @JsonProperty("_id")
  private String id;
  @JsonProperty("_score")
  private float score;
  @JsonProperty("matched_queries")
  private List<String> matchedQueries;
  @JsonProperty("_source")
  private T source;

  public boolean matchedQuery(String name) {
    return matchedQueries != null && matchedQueries.contains(name);
  }

  public String getId() {
    return id;
  }

  public float getScore() {
    return score;
  }

  public List<String> getMatchedQueries() {
    return matchedQueries;
  }

  public T getSource() {
    return source;
  }

}
