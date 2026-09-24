package life.catalogue.jobs.cron;

import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobExecutor;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Harvests the person registry every few days. Only scheduled when persons.harvestIntervalDays is set. It checks once a
 * day, the first time an hour after the server started, when the last harvest finished: a deploy restarts every
 * schedule, so a period of days counted from the start could keep a harvest from ever running.
 */
public class PersonHarvestCron extends CronJob {
  private static final Logger LOG = LoggerFactory.getLogger(PersonHarvestCron.class);
  private final JobExecutor exec;
  private final Supplier<LocalDateTime> lastHarvest;
  private final Supplier<? extends BackgroundJob> job;
  private final int days;

  /**
   * @param lastHarvest when the last harvest finished that succeeded, null for none
   * @param days        days to pass after it before the next harvest
   */
  public PersonHarvestCron(JobExecutor exec, Supplier<LocalDateTime> lastHarvest, Supplier<? extends BackgroundJob> job, int days) {
    super(1, 24, TimeUnit.HOURS);
    this.exec = exec;
    this.lastHarvest = lastHarvest;
    this.job = job;
    this.days = days;
  }

  @Override
  public void run() {
    try {
      LocalDateTime last = lastHarvest.get();
      if (last == null || last.isBefore(LocalDateTime.now().minusDays(days))) {
        exec.submit(job.get());
      }
    } catch (RuntimeException e) {
      // a cron job that throws is unscheduled by the executor, and this one must survive to run next time
      LOG.error("Failed to submit a person harvest", e);
    }
  }
}
