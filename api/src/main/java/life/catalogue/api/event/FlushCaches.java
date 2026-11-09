package life.catalogue.api.event;

/**
 * Asks every listener of every app to drop its caches, see {@link Listener#flushCaches()}.
 * Also sent in place of an event too big to be broadcast.
 */
public class FlushCaches implements Event {

  @Override
  public boolean equals(Object o) {
    return o instanceof FlushCaches;
  }

  @Override
  public int hashCode() {
    return FlushCaches.class.hashCode();
  }

  @Override
  public String toString() {
    return "FlushCaches";
  }
}
