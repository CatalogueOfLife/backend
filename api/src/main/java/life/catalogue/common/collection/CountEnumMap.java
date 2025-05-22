package life.catalogue.common.collection;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public class CountEnumMap<K extends Enum<K>> extends EnumMap<K, Integer> {

  public CountEnumMap(Class<K> keyType) {
    super(keyType);
  }

  /**
   * Increase counter by one or set to 1 if not existing already
   */
  public void inc(K key) {
    if (containsKey(key)) {
      put(key, get(key)+1);
    } else {
      put(key, 1);
    }
  }

  public void inc(K key, int diff) {
    if (containsKey(key)) {
      put(key, get(key)+diff);
    } else {
      put(key, diff);
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

  /**
   * @return an Optional describing the maximum count of this map, or an empty Optional if the map is empty
   */
  public Optional<Integer> highestCount() {
    return values().stream().max(Integer::compareTo);
  }

  /**
   * Similar to collection method, it will remove all entries from the map that pass the given filter
   */
  public boolean removeIf(Predicate<K> filter) {
    Objects.requireNonNull(filter);
    boolean removed = false;

    final Iterator<K> each = keySet().iterator();
    while (each.hasNext()) {
      if (filter.test(each.next())) {
        each.remove();
        removed = true;
      }
    }
    return removed;
  }
}
