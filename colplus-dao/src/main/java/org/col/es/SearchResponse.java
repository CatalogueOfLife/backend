package org.col.es;

/**
 * The outer shell of an Elasticsearch search response.
 *
 * @param <T> The type of objects in the search response (in our case EsNameUsage)
 */
public class SearchResponse<T> {

  private int took;
  private ResultSet<T> hits;

  public int getTook() {
    return took;
  }

  public ResultSet<T> getHits() {
    return hits;
  }

}
