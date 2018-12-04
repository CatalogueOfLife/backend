package org.col.es.query;

/**
 * Represents the (exact) value you are searching for, plus the option to provide a boost to documents containing that value. 
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
