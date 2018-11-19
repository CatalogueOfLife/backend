package org.col.es.util;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A tuple class accomodating one of the peculiarities of the Elasticsearch Query DSL.
 */
public class CollapsibleTuple<T, U> {

  private static class Tuple<X, Y> {
    final X x;
    final Y y;

    Tuple(X x, Y y) {
      this.x = x;
      this.y = y;
    }
  }

  private final Tuple<T, U> tuple;

  public CollapsibleTuple(T t) {
    this(t, null);
  }

  public CollapsibleTuple(T t, U u) {
    tuple = new Tuple<>(t, u);
  }

  public T getLeft() {
    return tuple.x;
  }

  public U getRight() {
    return tuple.y;
  }

  @JsonValue
  public Object jsonValue() {
    if (tuple.y == null) {
      return tuple.x;
    }
    return tuple;
  }

}
