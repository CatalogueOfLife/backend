package org.col.es.query;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Sort options when sorting on particular field.
 */
@SuppressWarnings("unused")
public class SortOptions {

  public static enum Order {
    ASC, DESC;

    @JsonValue
    public String toString() {
      return name().toLowerCase();
    }
  }

  public static enum Nulls {
    FIRST, LAST;

    @JsonValue
    public String toString() {
      return "_" + name().toLowerCase();
    }
  }

  public static enum Mode {
    MIN, MAX, SUM, AVG, MEDIAN;

    @JsonValue
    public String toString() {
      return name().toLowerCase();
    }
  }

  private final Order order;
  private final Nulls missing;
  private final Mode mode;

  public SortOptions() {
    this(Order.ASC);
  }

  public SortOptions(Order order) {
    this(order, null, null);
  }

  public SortOptions(Order order, Nulls nulls) {
    this(order, nulls, null);
  }

  public SortOptions(Order order, Mode mode) {
    this(order, null, mode);
  }

  public SortOptions(Order order, Nulls nulls, Mode mode) {
    this.order = order;
    this.missing = nulls;
    this.mode = mode;
  }

}
