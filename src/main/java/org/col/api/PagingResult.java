package org.col.api;

import java.util.List;

/**
 * A generic paging response wrapping a list payload
 * @param <T> the type of the paging content
 */
public class PagingResult<T> extends Page {
  private int total;
  private List<T> result;

  public int getTotal() {
    return total;
  }

  public void setTotal(int total) {
    this.total = total;
  }

  public List<T> getResult() {
    return result;
  }

  public void setResult(List<T> result) {
    this.result = result;
  }
}
