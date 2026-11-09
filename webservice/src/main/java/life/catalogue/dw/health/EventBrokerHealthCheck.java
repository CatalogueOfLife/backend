package life.catalogue.dw.health;


import life.catalogue.event.EventBroker;

import com.codahale.metrics.health.HealthCheck;

/**
 * Checks that the event broker receives events.
 */
public class EventBrokerHealthCheck extends HealthCheck {

  private final EventBroker broker;

  public EventBrokerHealthCheck(EventBroker broker) {
    this.broker = broker;
  }

  @Override
  protected Result check() throws Exception {
    return broker.isHealthy() ? Result.healthy(broker.status()) : Result.unhealthy(broker.status());
  }
}
