package org.col.common.func;

import java.util.function.Predicate;

public class Predicates {
  public static <T> Predicate<T> not(Predicate<T> t) {
    return t.negate();
  }
}
