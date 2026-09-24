package life.catalogue.jobs.cron;

import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobExecutor;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts a harvest of the person registry every few days. Only scheduled when persons.harvestIntervalDays is set.
 */
public class PersonHarvestCron extends CronJob {
  private static final Logger LOG = LoggerFactory.getLogger(PersonHarvestCron.class);
  private final JobExecutor exec;
  private final Supplier<? extends BackgroundJob> job;

  public PersonHarvestCron(JobExecutor exec, Supplier<? extends BackgroundJob> job, int days) {
    super(days, days, TimeUnit.DAYS);
    this.exec = exec;
    this.job = job;
  }

  @Override
  public void run() {
    try {
      exec.submit(job.get());
    } catch (RuntimeException e) {
      // a cron job that throws is unscheduled by the executor, and this one must survive to run next time
      LOG.error("Failed to submit a person harvest", e);
    }
  }
}
