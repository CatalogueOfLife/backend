package org.col.api;

import com.google.common.base.Preconditions;

import java.util.Objects;

/**
 * A page used for requesting or responding to a pageable service.
 */
public class Page {
  public static final int MAX_LIMIT = 1000;
  private int offset = 0;
  private int limit = 10;

  public Page() {
  }

  public Page(int limit) {
    this.limit = limit;
  }

  public Page(int offset, int limit) {
    Preconditions.checkArgument(offset >= 0, "Offset cannot be negative");
    Preconditions.checkArgument(limit >= 0, "Limit cannot be negative");
    Preconditions.checkArgument(limit <= MAX_LIMIT, "Maximum allowed Limit is 1000");
    this.offset = offset;
    this.limit = limit;
  }

  /**
   * @return number of records to skip before returning results. Defaults to 0.
   */
  public int getOffset() {
    return offset;
  }

  public void setOffset(int offset) {
    this.offset = offset;
  }

  /**
   * @return maximum number of records to return. Defaults to 10.
   */
  public int getLimit() {
    return limit;
  }

  public void setLimit(int limit) {
    this.limit = limit;
  }

  /**
   * Generates the next page parameters by increasing the offset according to the used limit.
   */
  public void next() {
    this.offset += limit;
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
