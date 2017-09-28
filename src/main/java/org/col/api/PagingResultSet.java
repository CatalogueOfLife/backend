package org.col.api;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * A generic paging response wrapping a list payload
 * @param <T> the type of the paging content
 */
public class PagingResultSet<T> extends Page {
  private final int total;
  private final List<T> result;

  public PagingResultSet(int total, List<T> result) {
    this.total = total;
    this.result = result;
  }

  @Nullable
  public int getTotal() {
    return total;
  }

  public List<T> getResult() {
    return result;
  }

  /**
   * @return true if this is the last page and there are no more pages with content if the offset is increased.
   */
  public boolean isLast() {
    return total <= getOffset() + getLimit();
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
