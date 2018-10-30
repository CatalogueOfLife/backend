package org.col.es.query;

public class QueryValue {

  private final Object value;
  private final Float boost;

  public QueryValue(Object value) {
    this(value, null);
  }

  public QueryValue(Object value, Float boost) {
    this.value = value;
    this.boost = boost;
  }

  public Object getValue() {
    return value;
  }

  public Float getBoost() {
    return boost;
  }

}
