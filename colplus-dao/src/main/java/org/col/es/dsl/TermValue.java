package org.col.es.dsl;

/**
 * Represents the (exact) value you are searching for, plus the option to boost matching documents. 
 */
public class TermValue {

  final Object value;
  final Float boost;

  public TermValue(Object value) {
    this(value, null);
  }

  public TermValue(Object value, Float boost) {
    this.value = value;
    this.boost = boost;
  }

}
