package org.col.es;

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
