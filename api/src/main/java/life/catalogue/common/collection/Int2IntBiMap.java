package life.catalogue.common.collection;

import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

/**
 * A composite primitive int map that keeps bidirectional mappings of unique integer combinations.
 * Quick implementation using fastutils outside of the regular collection frameworks.
 */
public class Int2IntBiMap {
  private final Int2IntMap regular = new Int2IntOpenHashMap();
  private final Int2IntMap reverse = new Int2IntOpenHashMap();

  public int size() {
    return regular.size();
  }

  public boolean isEmpty() {
    return regular.isEmpty();
  }

  public void clear() {
    regular.clear();
    reverse.clear();
  }

  public boolean containsKey(int key) {
    return regular.containsKey(key);
  }

  public boolean containsValue(int key) {
    return reverse.containsKey(key);
  }

  public int computeValueIfAbsent(final int key, final Int2IntFunction mappingFunction) {
    var value = regular.computeIfAbsent(key, mappingFunction);
    reverse.putIfAbsent(value, key);
    return value;
  }

  public int computeKeyIfAbsent(final int value, final Int2IntFunction mappingFunction) {
    var key = reverse.computeIfAbsent(value, mappingFunction);
    regular.putIfAbsent(key, value);
    return key;
  }

  public int putIfAbsent(int key, int value) {
    regular.putIfAbsent(key, value);
    reverse.putIfAbsent(value, key);
    return value;
  }

  public int put(int key, int value) {
    reverse.put(value, key);
    return regular.put(key, value);
  }

  public int getValue(int key) {
    return regular.get(key);
  }

  public int getKey(int value) {
    return reverse.get(value);
  }

  public int removeKey(int key) {
    int value = regular.remove(key);
    reverse.remove(value);
    return value;
  }

  public int removeValue(int value) {
    int key = reverse.remove(value);
    regular.remove(key);
    return key;
  }

}
