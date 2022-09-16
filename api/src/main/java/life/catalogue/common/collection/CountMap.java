package life.catalogue.common.collection;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntObjectPair;

import java.util.EnumMap;

public class CountMap<K extends Enum<K>> extends EnumMap<K, Integer> {

  public CountMap(Class<K> keyType) {
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

}
