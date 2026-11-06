package life.catalogue.common.collection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class MapUtils {
  
  private MapUtils() {
    throw new UnsupportedOperationException("Can't initialize class");
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
