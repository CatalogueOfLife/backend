package life.catalogue.dw.health;


import life.catalogue.event.EventBroker;

import com.codahale.metrics.health.HealthCheck;

/**
 * Checks that the event broker is polling events fine.
 */
public class EventBrokerHealthCheck extends HealthCheck {

  private final EventBroker broker;

  public EventBrokerHealthCheck(EventBroker broker) {
    this.broker = broker;
  }
  
  @Override
  protected Result check() throws Exception {
    if (broker.hasStarted()) {
      if (broker.isAlive()) {
        return Result.unhealthy("Event broker thread is dead");
      }
      return Result.healthy("Event broker is online and alive");
    }
    return Result.healthy("Event broker is offline");
  }
}