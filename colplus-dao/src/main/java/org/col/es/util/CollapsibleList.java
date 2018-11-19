package org.col.es.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * An extension of ArrayList solely aimed at accomodating the Elasticsearch Query DSL. Single-element arrays can be written without using
 * array notation (square brackets). You can still use array notation for single-element arrays, but it makes the query harder to read.
 */
public class CollapsibleList<E> extends ArrayList<E> {

  public static <T> CollapsibleList<T> of(T one) {
    return new CollapsibleList<>(Arrays.asList(one));
  }

  public static <T> CollapsibleList<T> of(T one, T two) {
    return new CollapsibleList<>(Arrays.asList(one, two));
  }

  public CollapsibleList() {
    super();
  }

  public CollapsibleList(Collection<? extends E> c) {
    super(c);
  }

  @JsonValue
  public Object jsonValue() {
    switch (size()) {
      case 0:
        return null;
      case 1:
        return get(0);
      default:
        return stream().toArray();
    }
  }

}
