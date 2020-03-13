package life.catalogue.common.collection;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * A HashMap that comes with a default supplier for missing keys
 * if get(key) is called.
 *
 * Analoguous to:
 * <code>
 *   map.computeIfAbsent(key, k -> defaultSupplier.get()).get(key)
 * </code>
 * but without the need to specify the supplier and computeIfAbsent every time
 */
public class DefaultMap<K, V> extends HashMap<K, V> {
  private final Supplier<V> defaultSupplier;
  
  public static <K> DefaultMap<K, AtomicInteger> createCounter() {
    return new DefaultMap<K, AtomicInteger>(() -> new AtomicInteger(0));
  }
  
  public DefaultMap(Supplier<V> defaultSupplier) {
    this.defaultSupplier = defaultSupplier;
  }
  
  @Override
  public V get(Object key) {
    computeIfAbsent((K) key, k -> defaultSupplier.get());
    return super.get(key);
  }
}
