package org.col.api;

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
}
