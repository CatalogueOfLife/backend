package org.col.api;

import java.util.List;
import java.util.Objects;

/**
 * A generic paging response wrapping a list payload
 * @param <T> the type of the paging content
 */
public class PagingResultSet<T> extends Page {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PagingResultSet<?> resultSet = (PagingResultSet<?>) o;
    return total == resultSet.total &&
        Objects.equals(result, resultSet.result);
  }

  @Override
  public int hashCode() {
    return Objects.hash(total, result);
  }
}
