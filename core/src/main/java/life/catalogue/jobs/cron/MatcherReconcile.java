package life.catalogue.jobs.cron;

import life.catalogue.matching.UsageMatcherFactory;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the persistent usage matcher stores in step with the datasets and the names index they were built
 * against: rebuilds the stale ones, reaps the obsolete ones and ages out the on demand ones.
 * <p>
 * With a zero delay this also runs once at startup, which is what the UsageMatcher component used to be for.
 * It is a cron job rather than a component because nothing was ever gated on that component having started -
 * matching, sector sync, XRelease and every /matcher endpoint work without it - so all it really provided was
 * this one trigger, while its start() and stop() carried real hazards of their own. A nidx swap in particular
 * leaves every matcher stale without changing any dataset's import attempt, and only this pass notices.
 */
public class MatcherReconcile extends CronJob {
  private static final Logger LOG = LoggerFactory.getLogger(MatcherReconcile.class);

  private final UsageMatcherFactory factory;

  public MatcherReconcile(UsageMatcherFactory factory) {
    // no delay, so a server that just came up reconciles before anything matches against a stale store
    super(0, 1, TimeUnit.DAYS);
    this.factory = factory;
  }

  @Override
  public void run() {
    try {
      factory.maintenance();
    } catch (RuntimeException e) {
      // a cron job that throws is unscheduled by the executor, and this one must survive to run tomorrow
      LOG.error("Failed to reconcile the usage matchers", e);
    }
  }
}
