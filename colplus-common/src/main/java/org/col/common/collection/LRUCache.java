package org.col.common.collection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Last Recently Used cache using a LinkedHashMap underneath.
 * This class is NON THREAD SAFE !
 */
public class LRUCache<K, V> extends LinkedHashMap<K, V> {
  private final int maxEntries;
  private static final int DEFAULT_INITIAL_CAPACITY = 16; // same as HasMap
  private static final float DEFAULT_LOAD_FACTOR = 0.75f; // same as HasMap

  public LRUCache(int initialCapacity,
                  float loadFactor,
                  int maxEntries) {
    super(initialCapacity, loadFactor, true);
    this.maxEntries = maxEntries;
  }

  public LRUCache(int initialCapacity,
                  int maxEntries) {
    this(initialCapacity, DEFAULT_LOAD_FACTOR, maxEntries);
  }

  public LRUCache(int maxEntries) {
    this(DEFAULT_INITIAL_CAPACITY, maxEntries);
  }

  @Override
  protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
    return size() > maxEntries;
  }
}

