package org.col.es.response;

/**
 * The outer shell of an Elasticsearch search response.
 *
 * @param <T> The type of objects in the search response.
 */
public class EsSearchResponse<T> {

  private int took;
  private SearchHits<T> hits;

  public int getTook() {
    return took;
  }

  public SearchHits<T> getHits() {
    return hits;
  }
  
}
