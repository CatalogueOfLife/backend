package org.col.es.response;

/**
 * The response object for an Elasticsearch query without aggregations.
 * 
 * @param <T> The type of objects in the response
 * 
 */
public class EsQueryResponse<T> {

  private int took;
  private SearchHits<T> hits;

  public int getTook() {
    return took;
  }

  public SearchHits<T> getHits() {
    return hits;
  }

}
