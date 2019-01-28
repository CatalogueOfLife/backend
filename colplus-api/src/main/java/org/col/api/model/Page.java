package org.col.api.model;

import java.util.Objects;
import java.util.Set;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

/**
 * A page used for requesting or responding to a pageable service.
 */
public class Page {
  public static final int MAX_LIMIT = 1000;
  public static final int DEFAULT_LIMIT = 10;
  public static final int DEFAULT_OFFSET = 0;
  public static final Set<String> PARAMETER_NAMES = ImmutableSet.of("offset", "limit");
  
  @QueryParam("offset")
  @DefaultValue("0")
  @Min(0)
  private int offset;
  
  @QueryParam("limit")
  @DefaultValue("10")
  @Min(0)
  @Max(MAX_LIMIT)
  private int limit;
  
  public Page() {
    this(DEFAULT_OFFSET, DEFAULT_LIMIT);
  }
  
  public Page(int limit) {
    this(DEFAULT_OFFSET, limit);
  }
  
  public Page(int offset, int limit) {
    Preconditions.checkArgument(offset >= 0, "offset needs to be positive");
    Preconditions.checkArgument(limit >= 0 && limit <= MAX_LIMIT, "limit needs to be between 0-1000");
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
  
  @Override
  public String toString() {
    return "Page{" +
        "offset=" + offset +
        ", limit=" + limit +
        '}';
  }
}
