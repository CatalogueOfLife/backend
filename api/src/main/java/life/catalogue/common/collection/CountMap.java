package life.catalogue.common.collection;

import java.util.HashMap;

public class CountMap<K> extends HashMap<K, Integer> {

  /**
   * Increase counter by one or set to 1 if not existing already
   */
  public void inc(K key) {
    merge(key,1, Integer::sum);
  }

  public void inc(K key, int diff) {
    merge(key,diff, Integer::sum);
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
