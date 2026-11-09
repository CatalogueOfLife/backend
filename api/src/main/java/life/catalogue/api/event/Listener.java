package life.catalogue.api.event;

/**
 * Marker for objects that receive broker events, see the more specific sub interfaces.
 */
public interface Listener {

  /**
   * Drops everything cached. Called when events may have been missed, e.g. after the broker lost its connection,
   * and for a FlushCaches event.
   */
  default void flushCaches() {
    // nothing by default
  }
}
