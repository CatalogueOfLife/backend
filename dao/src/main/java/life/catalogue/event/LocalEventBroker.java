package life.catalogue.event;

import life.catalogue.api.event.Event;

/**
 * Delivers events within this JVM only, synchronously on the publishing thread and whether started or not.
 * For command line tools, a server with a database of its own like the release bundle, and tests.
 */
public class LocalEventBroker extends AbstractEventBroker {
  private volatile boolean started;

  @Override
  public void publish(Event event) {
    dispatch(event);
  }

  @Override
  public void start() {
    started = true;
  }

  @Override
  public void stop() {
    started = false;
  }

  @Override
  public boolean hasStarted() {
    return started;
  }

  @Override
  public boolean isHealthy() {
    return true;
  }

  @Override
  public String status() {
    return "Local event broker within this JVM";
  }
}
