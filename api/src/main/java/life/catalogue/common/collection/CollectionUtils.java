package life.catalogue.common.collection;

import java.util.*;
import java.util.function.Predicate;

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
   * Creates a list from an array of values that can include null values.
   */
  public static <T> List<T> list(T... args) {
    List<T> list = new ArrayList<>();
    list.addAll(Arrays.asList(args));
    return list;
  }

  public static <T> ArrayList<T> mutable(List<T> arg) {
    return new ArrayList<>(arg);
  }

  public static <K,V> HashMap<K,V> mutable(Map<K,V> arg) {
    return new HashMap<>(arg);
  }
  public static <K> CountMap<K> countable(Map<K,Integer> arg) {
    return new CountMap<>(arg);
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
   * Returns the last object from the given list or null if the list is empty
   */
  public static <T> T lastOrNull(List<T> objects) {
    if (objects == null || objects.isEmpty()) return null;
    return objects.getLast();
  }

  public static <E> int lastIndexOf(final Iterable<E> iterable, final Predicate<? super E> predicate) {
    int lastIdx = -1;
    if (iterable != null) {
      var iter = iterable.iterator();
      for(int idx = 0; iter.hasNext(); idx++) {
        final E element = iter.next();
        if (predicate.test(element)) {
          lastIdx = idx;
        }
      }
    }
    return lastIdx;
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

  public static List<Integer> range(int first, int last) {
    return range(first, last, 1);
  }

  public static List<Integer> range(int first, int last, int step) {
    var range = new ArrayList<Integer>();
    for (int i = first; i <= last; i=i+step) {
      range.add(i);
    }
    return range;
  }

  public static <T> boolean overlaps(Collection<T> c1, Collection<T> c2) {
    if (c1 == null || c2 == null) return false;
    for (var x : c1) {
      for (var y : c2) {
        if (x.equals(y)) {
          return true;
        }
      }
    }
    return false;
  }

  private CollectionUtils() {
  }

}
