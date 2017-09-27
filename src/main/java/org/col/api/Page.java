package org.col.api;

import java.util.Objects;

/**
 * A page used for requesting or responding to a pageable service.
 */
public class Page {
  private int offset;
  private int limit;

  public int getOffset() {
    return offset;
  }

  public void setOffset(int offset) {
    this.offset = offset;
  }

  public int getLimit() {
    return limit;
  }

  public void setLimit(int limit) {
    this.limit = limit;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Page page = (Page) o;
    return offset == page.offset &&
        limit == page.limit;
  }

  @Override
  public int hashCode() {
    return Objects.hash(offset, limit);
  }
}
