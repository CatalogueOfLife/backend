package life.catalogue.es.response;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/*
 * For the query part of the CoLPlus ES code, this is the only class that can't easily be ported from ES6 to ES7. The
 * indexing code also has to deal with change between ES6 and ES7, but there we can do it elegantly and transparently.
 * We don't make any attempt at elegance here and just wait till we drop support for ES6.
 */
public class SearchHits<T> {

  // Ugly but required while we support both ES6 and ES7. In ES6 the "total" field in the response is just an integer. In
  // ES7 it's something more elaborate, which we simply deserialize into a map. Once we drop support for ES6 we will
  // create a special class for it.
  private Object total;
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

  // Remove this method when dropping support for ES6!
  public int getTotalNumHits() {
    if (total != null && total instanceof Integer) {
      // We are dealing with ES6
      return (Integer) total;
    }
    // We are dealing with ES7
    @SuppressWarnings("unchecked")
    Map<String, Object> m = (Map<String, Object>) total;
    return (Integer) m.get("value");
  }

}
