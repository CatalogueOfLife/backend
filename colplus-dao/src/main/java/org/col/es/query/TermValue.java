package org.col.es.query;

public class TermValue {
  
  private final Object value;
  private final Float boost;
  
  public TermValue(Object value) {
    this(value, null);
  }
  
  public TermValue(Object value, Float boost) {
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
