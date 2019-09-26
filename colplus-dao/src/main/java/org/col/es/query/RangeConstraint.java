package org.col.es.query;

@SuppressWarnings("unused")
class RangeConstraint<T> extends Constraint {

  private T lt;
  private T gt;
  private T lte;
  private T gte;

  void lt(T value) {
    this.lt = value;
  }

  void gt(T value) {
    this.gt = value;
  }

  void lte(T value) {
    this.lte = value;
  }

  void gte(T value) {
    this.gte = value;
  }
}
