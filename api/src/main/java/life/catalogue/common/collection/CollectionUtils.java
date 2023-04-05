package life.catalogue.common.collection;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

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
   * Nullsafe size method. Returns the size of a collection or 0 if its null.
   */
  public static int size(Collection<?> c) {
    return c == null ? 0 : c.size();
  }

  /**
   * Returns a new (!) list with all nulls removed.
   * We don't change the list in place to allow for immutable input
   */
  public static <T> List<T> removeNull(List<T> c) {
    if (c == null) return null;
    return c.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
  }

  /**
   * Iterate over 2 collections in parallel
   */
  public static <T, U> void zip(Collection<T> ct, Collection<U> cu, BiConsumer<T, U> each) {
    Iterator<T> it = ct.iterator();
    Iterator<U> iu = cu.iterator();
    while (it.hasNext() && iu.hasNext()) {
      each.accept(it.next(), iu.next());
    }
  }

  /**
   * Creates a list from an array of values that can include null values.
   */
  public static <T> List<T> list(T... args) {
    List<T> list = new ArrayList<>();
    list.addAll(Arrays.asList(args));
    return list;
  }

  /**
   * Returns the first object from the given list if it matches the given predicate.
   */
  public static <T> T first(Collection<T> objects, Predicate<T> filter) {
    for (var obj : objects) {
      if (filter.test(obj)) {
          return obj;
      }
    }
    return null;
  }

  /**
   * Returns the objects from the given list that match the given predicate.
   */
  public static <T> List<T> find(Collection<T> objects, Predicate<T> filter) {
    List<T> matches = new ArrayList<>();
    for (var obj : objects) {
      if (filter.test(obj)) {
        matches.add(obj);
      }
    }
    return matches;
  }

  /**
   * Returns an object from the given list if it is the only object that matches the given predicate.
   * If there are no or multiple matches null will be returned.
   */
  public static <T> T findSingle(Collection<T> objects, Predicate<T> filter) {
    T match = null;
    for (var obj : objects) {
      if (filter.test(obj)) {
        if (match != null) {
          // multiple matches
          return null;
        } else {
          match = obj;
        }
      }
    }
    return match;
  }

  public static boolean equals(List<?> first, Object[] second) {
    if (first == null & second == null) return true;
    if (first == null || second == null) return false;

    if (first.size() != second.length) return false;
    int idx = 0;
    for (Object o1 : first) {
      if (!o1.equals(second[idx])) {
        return false;
      }
      idx++;
    }
    return true;
  }

  /**
   * Compact an array, 'removing' any element that is null
   *
   * @param original The array to be compacted
   * @return The compacted array
   */
  public static <T> T[] compact(T[] original) {
    T[] result = null;
    int ix = 0;

    for (int i = 0; i < original.length; i++) {
      if (original[i] != null) {
        original[ix++] = original[i];
      }
    }

    if (ix != original.length) {
      int i;

      for (i = 0; (i < original.length) && (original[i] == null); i++) {
      }

      if (i == original.length) {
        throw new RuntimeException("All elements null. Cannot determine element type");
      }

      result = (T[]) Array.newInstance(original[i].getClass(), ix);
      System.arraycopy(original, 0, result, 0, result.length);
    }

    return result;
  }

  public static IntSet intSetOf(Collection<Integer> values) {
    IntSet is = new IntOpenHashSet();
    if (values != null) {
      is.addAll(values);
    }
    return is;
  }

  public static IntSet intSetOf(int... values) {
    IntSet is = new IntOpenHashSet();
    if (values != null) {
      for (int i : values) {
        is.add(i);
      }
    }
    return is;
  }

  public static <T> T[] concat(T[] first, T[]... rest) {
    int totalLength = first.length;
    for (T[] array : rest) {
      totalLength += array.length;
    }
    T[] result = Arrays.copyOf(first, totalLength);
    int offset = first.length;
    for (T[] array : rest) {
      System.arraycopy(array, 0, result, offset, array.length);
      offset += array.length;
    }
    return result;
  }

  private CollectionUtils() {
  }

}
