package life.catalogue.es.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents a single search hit.
 *
 * @param <T> The Java type modeling to the index being searched.
 */
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
