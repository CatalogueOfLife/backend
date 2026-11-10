package life.catalogue.dw.health;

import life.catalogue.outbox.OutboxRunner;

import com.codahale.metrics.health.HealthCheck;

/**
 * Reports commands that have waited in the outbox for too long while its runner is started, a sign that their
 * handler is stuck. A stopped runner, the old app of a deploy, is healthy: its commands wait on purpose, and so do
 * those of a handler that is offline, like the DOI commands while the DoiUpdater is stopped to pause DataCite.
 */
public class OutboxHealthCheck extends HealthCheck {
  private final OutboxRunner runner;

  public OutboxHealthCheck(OutboxRunner runner) {
    this.runner = runner;
  }

  @Override
  protected Result check() throws Exception {
    if (!runner.hasStarted()) {
      return Result.healthy("Outbox runner is stopped");
    }
    var h = runner.health();
    String msg = String.format("%s pending, %s overdue by more than %s minutes, %s failed",
      h.pending(), h.overdue(), runner.getCfg().overdueMinutes, h.failed());
    return h.overdue() > 0 ? Result.unhealthy(msg) : Result.healthy(msg);
  }
}
