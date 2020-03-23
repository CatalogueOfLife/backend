package life.catalogue.es.response;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;

/**
 * The "hits" object within the ES search response object
 *
 * @param <T> The Java type modeling to the index being searched.
 */
public class SearchHits<T> {

  private Total total;
  @JsonProperty("max_score")
  private float maxScore;
  private List<SearchHit<T>> hits;

  public Object getTotal() {
    return total;
  }

  public float getMaxScore() {
    return maxScore;
  }

  public List<SearchHit<T>> getHits() {
    return hits;
  }

  public int getTotalNumHits() {
    Preconditions.checkNotNull(total, "total must not be null");
    return total.getValue();
  }

}
