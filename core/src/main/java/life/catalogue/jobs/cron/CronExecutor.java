package life.catalogue.jobs.cron;

import life.catalogue.common.Managed;
import life.catalogue.concurrent.NamedThreadFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;


public class CronExecutor implements Managed {
  private static final Logger LOG = LoggerFactory.getLogger(CronExecutor.class);
  private final String THREAD_NAME = "cron-executor";
  private final CronJob[] jobs;
  private ScheduledExecutorService scheduler;
  private List<ScheduledFuture<?>> futures = new ArrayList<>();

  public static CronExecutor startWith(CronJob... jobs) {
    Preconditions.checkNotNull(jobs, "At least one cron job must be specified");
    return new CronExecutor(jobs);
  }

  private CronExecutor(CronJob[] jobs) {
    this.jobs = jobs;
  }

  @Override
  public void start() throws Exception {
    if (scheduler == null && jobs.length > 0) {
      LOG.info("Start cron executor with {} jobs", jobs.length);
      scheduler = Executors.newScheduledThreadPool(1,
        new NamedThreadFactory(THREAD_NAME, Thread.NORM_PRIORITY, true)
      );
      for (CronJob job : jobs) {
        LOG.info("Schedule cron job {} every {} {}", job.getClass().getSimpleName(), job.getFrequency(), job.getFrequencyUnit());
        var f = scheduler.scheduleAtFixedRate(job, job.getDelay(), job.getFrequency(), job.getFrequencyUnit());
        futures.add(f);
      }
    }
  }

  @Override
  public void stop() throws Exception {
    LOG.info("Stopping cron executor with {} jobs", futures.size());
    for (var f : futures) {
      f.cancel(true);
    }
    scheduler.shutdown();
    LOG.info("Cron executor stopped");
    scheduler = null;
  }

  @Override
  public boolean hasStarted() {
    return scheduler != null;
  }
}
