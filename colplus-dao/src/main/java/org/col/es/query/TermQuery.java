package org.col.es.query;

public class TermQuery {

  private Object value;
  private Float boost;

  public TermQuery() {}

  public TermQuery(Object value) {
    this.value = value;
  }

  public TermQuery(Object value, Float boost) {
    this.value = value;
    this.boost = boost;
  }

  public Object getValue() {
    return value;
  }

  public void setValue(Object value) {
    this.value = value;
  }

  public Float getBoost() {
    return boost;
  }

  public void setBoost(Float boost) {
    this.boost = boost;
  }

}
