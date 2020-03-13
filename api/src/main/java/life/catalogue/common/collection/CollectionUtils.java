package life.catalogue.common.collection;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Collection related methods. In order to prevent and pre-empt subtle logical errors, all "Venn-diagram methods" (like
 * firstIsSubsetOfSecond) simply and consistently require all arguments to be non-empty. Equivalent but inverse methods (like
 * secondIsSubsetOfFirst) are provided b/c it may better suit how you think about the collections you are dealing with.
 */
public class CollectionUtils {

  /**
   * Returns true if specified collection is null or empty
   */
  public static boolean isEmpty(Collection<?> c) {
    return c == null || c.isEmpty();
  }

  /**
   * Returns true if specified collection is not null and contains at least one element.
   */
  public static boolean notEmpty(Collection<?> c) {
    return c != null && !c.isEmpty();
  }

  /**
   * Returns true if specified map is null or empty
   */
  public static boolean isEmpty(Map<?, ?> m) {
    return m == null || m.isEmpty();
  }

  /**
   * Returns true if specified collection is not null and contains at least one element.
   */
  public static boolean notEmpty(Map<?, ?> m) {
    return m != null && !m.isEmpty();
  }

  /**
   * Iterate over 2 collections in parallel
   */
  public static <T,U> void zip(Collection<T> ct, Collection<U> cu, BiConsumer<T,U> each) {
    Iterator<T> it = ct.iterator();
    Iterator<U> iu = cu.iterator();
    while (it.hasNext() && iu.hasNext()) {
      each.accept(it.next(), iu.next());
    }
  }

  private CollectionUtils() {}

}
