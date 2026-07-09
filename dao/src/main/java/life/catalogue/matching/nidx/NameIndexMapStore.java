package life.catalogue.matching.nidx;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Heap-backed NameIndexStore for memory/test configurations.
 * A pure {@code normalized-String -> nidx-int} registry.
 */
public class NameIndexMapStore implements NameIndexStore {
  private static final Logger LOG = LoggerFactory.getLogger(NameIndexMapStore.class);

  private ConcurrentHashMap<String, Integer> names;
  private final AtomicInteger maxKey = new AtomicInteger(0);
  private LocalDateTime created;
  private boolean started = false;

  @Override
  public void start() {
    names = new ConcurrentHashMap<>();
    maxKey.set(0);
    created = LocalDateTime.now();
    started = true;
    LOG.info("Names index memory store started");
  }

  @Override
  public void stop() {
    started = false;
    names = null;
  }

  @Override
  public boolean hasStarted() {
    return started && names != null;
  }

  @Override
  public int get(String normalized) {
    assertOnline();
    Integer k = names.get(normalized);
    return k == null ? 0 : k;
  }

  @Override
  public void add(String normalized, int nidx) {
    assertOnline();
    Integer prev = names.put(normalized, nidx);
    if (prev != null && prev != nidx) {
      LOG.warn("Names index bucket >{}< already had key {} - overwriting with new key {}", normalized, prev, nidx);
    }
    maxKey.accumulateAndGet(nidx, Math::max);
  }

  @Override
  public boolean contains(String normalized) {
    assertOnline();
    return names.containsKey(normalized);
  }

  @Override
  public int count() {
    assertOnline();
    return names.size();
  }

  @Override
  public int maxKey() {
    return maxKey.get();
  }

  @Override
  public void clear() {
    assertOnline();
    names.clear();
    maxKey.set(0);
    created = LocalDateTime.now();
  }

  @Override
  public Iterable<Map.Entry<String, Integer>> entries() {
    assertOnline();
    return names.entrySet();
  }

  @Override
  public void compact() {
    // heap map: nothing to compact.
  }

  @Override
  public LocalDateTime created() {
    return created;
  }
}
