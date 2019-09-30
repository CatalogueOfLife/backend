package org.col.common.func;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.col.api.util.VocabularyUtils;

public class Predicates {

  // public static <T> Predicate<T> not(Predicate<T> t) {
  // return t.negate();
  // }

  public static Predicate<String> isIntegerString = (s) -> {
    try {
      Integer.valueOf(s);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  };

  public static Predicate<String> isNumberString = (s) -> {
    try {
      Double.valueOf(s);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  };

  @SuppressWarnings("unchecked")
  public static BiPredicate<String, Class<?>> isEnumString = (name, vocab) -> {
    return VocabularyUtils.lookup(name, (Class<? extends Enum<?>>) vocab).isPresent();
  };
  
  public static <T> Predicate<T> not(Predicate<T> t) {
    return t.negate();
  }
}
