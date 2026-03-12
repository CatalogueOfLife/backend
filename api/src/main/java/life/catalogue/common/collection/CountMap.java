package life.catalogue.common.collection;

import java.util.HashMap;
import java.util.Map;

public class CountMap<K> extends HashMap<K, Integer> {

  public CountMap() {
  }

  public CountMap(Map<? extends K, ? extends Integer> m) {
    super(m);
  }

  /**
   * Increase counter by one or set to 1 if not existing already
   */
  public void inc(K key) {
    if (key != null) {
      merge(key,1, Integer::sum);
    }
  }

  /**
   * Changes counter by the amount given as diff
   */
  public void inc(K key, int diff) {
    if (key != null) {
      merge(key,diff, Integer::sum);
    }
  }

  /**
   * Adds an entire map of counts, summing up both entries for each key in both maps.
   */
  public void inc(CountMap<K> map) {
    if (map != null && !map.isEmpty()) {
      for (var entry : map.entrySet()) {
        inc(entry.getKey(), entry.getValue());
      }
    }
  }

  /**
   * Decreases the value linked to the given key by one.
   * @param key
   */
  public void dec(K key) {
    if (key != null) {
      merge(key,-1, Integer::sum);
    }
  }

  /**
   * Decreases the value linked to the given key by the given amount.
   * @param key
   * @param diff
   */
  public void dec(K key, int diff) {
    if (key != null) {
      merge(key,-diff, Integer::sum);
    }
  }

  /**
   * Decreases all values by the given amount.
   * @param map
   */
  public void dec(CountMap<K> map) {
    if (map != null && !map.isEmpty()) {
      for (var entry : map.entrySet()) {
        dec(entry.getKey(), entry.getValue());
      }
    }
  }

  /**
   * Multiplies all values by the given factor.
   * @param factor
   */
  public void multiply(int factor) {
    for (var entry : entrySet()) {
      entry.setValue(entry.getValue() * factor);
    }
  }

  /**
   * @return the key with the single, highest value or null if there is no key or more than one having the same highest number
   */
  public K highest() {
    K best = null;
    int highest = -1;
    for (var entry : entrySet()) {
      if (highest < entry.getValue()) {
        highest = entry.getValue();
        best = entry.getKey();
      } else if (highest == entry.getValue()) {
        best = null; // reset match if it's the same
      }
    }
    return best;
  }

  public int total() {
    return values().stream().mapToInt(Integer::intValue).sum();
  }
}
