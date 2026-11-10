package life.catalogue.dw.health;

import life.catalogue.outbox.OutboxRunner;

import com.codahale.metrics.health.HealthCheck;

/**
 * Reports commands that have waited in the outbox for too long while its runner is started, a sign that their
 * handler is stuck: overdue ones, and those requested long ago that keep being put off.
 * A stopped runner, the old app of a deploy, is healthy: its commands wait on purpose, and so do
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
    var cfg = runner.getCfg();
    String msg = String.format("%s pending, %s overdue by more than %s minutes, %s requested more than %s hours ago, %s failed",
      h.pending(), h.overdue(), cfg.overdueMinutes, h.stuck(), cfg.stuckHours, h.failed());
    return h.overdue() > 0 || h.stuck() > 0 ? Result.unhealthy(msg) : Result.healthy(msg);
  }
}
