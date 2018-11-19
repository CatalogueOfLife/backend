package org.col.es.query;

/**
 * Basically just the (exact) value you are searching for, plus the option to provide a boost to documents containing that value. 
 */
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
