package life.catalogue.event;

import life.catalogue.api.event.Event;
import life.catalogue.api.event.Listener;
import life.catalogue.common.Managed;

import java.util.List;

/**
 * Broadcasts events to the registered listeners of every app sharing the database: the read-write and the read-only
 * server and both apps of a blue-green deploy. Meant for cache invalidation and reactions that are harmless to repeat
 * or miss. An app only receives what is published while it runs, and every app receives everything, so anything that
 * must happen exactly once is an outbox command instead, see life.catalogue.outbox.Outbox.
 *
 * Events published before {@link #start()} are delivered once it is started.
 */
public interface EventBroker extends Managed, AutoCloseable {

  void register(Listener listener);

  void publish(Event event);

  /**
   * @return true if events are being received
   */
  boolean isHealthy();

  /**
   * @return a human readable status
   */
  String status();

  /**
   * @return the latest events delivered to this app, newest last
   */
  List<String> recent();

  @Override
  default void close() throws Exception {
    stop();
  }
}
