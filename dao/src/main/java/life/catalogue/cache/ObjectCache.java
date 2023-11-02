package life.catalogue.cache;

import life.catalogue.api.model.HasID;

import java.util.HashMap;
import java.util.Iterator;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple KVP style cache for instances keyed by a string.
 */
public interface ObjectCache<T extends HasID<String>> extends AutoCloseable, Iterable<T> {
  Logger LOG = LoggerFactory.getLogger(ObjectCache.class);

  boolean contains(String id);

  T get(String id);

  void put(T obj);

  void remove(String id);

  int size();

  @Override
  void close();

  /**
   * A simple cache backed by an in memory hash map that grows forever.
   * Really only for tests...
   */
  static <T extends HasID<String>> ObjectCache<T> hashMap() {
    return new ObjectCache<T>() {
      private final HashMap<String, T> data = new HashMap<>();

      @NotNull
      @Override
      public Iterator<T> iterator() {
        return data.values().iterator();
      }

      @Override
      public void close() {
      }

      @Override
      public boolean contains(String id) {
        return data.containsKey(id);
      }

      @Override
      public T get(String id) {
        return data.get(id);
      }

      @Override
      public void put(T obj) {
        data.put(obj.getId(), obj);
      }

      @Override
      public void remove(String id) {
        data.remove(id);
      }

      @Override
      public int size() {
        return data.size();
      }
    };
  }
}
