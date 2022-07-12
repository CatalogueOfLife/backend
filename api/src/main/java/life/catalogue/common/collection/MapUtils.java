package life.catalogue.common.collection;

import java.util.*;

public class MapUtils {
  
  private MapUtils() {
    throw new UnsupportedOperationException("Can't initialize class");
  }
  
  /**
   * This orders a Map by its values and returns a new {@link LinkedHashMap} which maintains that order.
   *
   * @param map to sort
   * @param <K> type of the map key
   * @param <V> type of the map value, this needs to implement {@link Comparable}
   * @return a map ordered by the values of the input map
   */
  public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
    return sortByValueInternal(map, new Comparator<Map.Entry<K, V>>() {
      public int compare(Map.Entry<K, V> o1, Map.Entry<K, V> o2) {
        return o1.getValue().compareTo(o2.getValue());
      }
    });
  }

  public static<K> void increment(Map<K, Integer> map, K key) {
    increment(map, key, 1);
  }

  public static<K> void increment(Map<K, Integer> map, K key, int inc) {
    Integer count = map.getOrDefault(key, 0);
    map.put(key, count + inc);
  }

  /**
   * Order a Map by its values using the given value comparator and return a new {@link LinkedHashMap} which maintains that order.
   *
   * @param map to sort
   * @param <K> type of the map key
   * @param <V> type of the map value
   * @return a map ordered by the values of the input map
   */
  public static <K, V> Map<K, V> sortByValue(Map<K, V> map, final Comparator<V> comparator) {
    return sortByValueInternal(map, new Comparator<Map.Entry<K, V>>() {
      public int compare(Map.Entry<K, V> o1, Map.Entry<K, V> o2) {
        return comparator.compare(o1.getValue(), o2.getValue());
      }
    });
  }
  
  private static <K, V> Map<K, V> sortByValueInternal(Map<K, V> map, final Comparator<Map.Entry<K, V>> comp) {
    List<Map.Entry<K, V>> list = new LinkedList<Map.Entry<K, V>>(map.entrySet());
    Collections.sort(list, comp);
    
    Map<K, V> result = new LinkedHashMap<K, V>(list.size());
    for (Map.Entry<K, V> entry : list) {
      result.put(entry.getKey(), entry.getValue());
    }
    return result;
  }
  
  public static long sumValues(Map<?, ? extends Number> map) {
    return map.values().stream().mapToLong(Number::longValue).sum();
  }

  public static <V, K> Map<V, K> invert(Map<K, V> map) {
    Map<V, K> inv = new HashMap<>();
    for (Map.Entry<K, V> entry : map.entrySet()) {
      inv.put(entry.getValue(), entry.getKey());
    }
    return inv;
  }

  /**
   * Builds an unmodifiable LinkedHashMap based on pairs given as an array of variable length.
   * Make sure instances are of correct type and can be cast!
   */
  public static <K, V> Map<K, V> linkedHashMap(Object... pairedItems) {
    LinkedHashMap<K, V> map = new LinkedHashMap<>();
    if (pairedItems.length % 2 != 0) {
      throw new IllegalArgumentException("List of pairs required, but found "+pairedItems.length);
    }
    K key = null;
    for (Object x : pairedItems) {
      if (key == null) {
        key = (K) x;
      } else {
        map.put(key, (V) x);
        key = null;
      }
    }
    return Collections.unmodifiableMap(map);
  }
}
