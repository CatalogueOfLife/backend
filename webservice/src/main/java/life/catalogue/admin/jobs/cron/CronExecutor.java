package life.catalogue.admin.jobs.cron;

import com.google.common.base.Preconditions;

import life.catalogue.concurrent.NamedThreadFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.lifecycle.Managed;

public class CronExecutor implements Managed {
  private static final Logger LOG = LoggerFactory.getLogger(CronExecutor.class);
  private final String THREAD_NAME = "cron-executor";
  private ScheduledExecutorService scheduler;
  private List<ScheduledFuture<?>> futures = new ArrayList<>();

  public static CronExecutor startWith(CronJob... jobs) {
    Preconditions.checkNotNull(jobs, "At least one cron job must be specified");
    CronExecutor cron = new CronExecutor();
    for (CronJob job : jobs) {
      LOG.info("Schedule cron job {} every {} {}", job.getClass().getSimpleName(), job.getFrequency(), job.getFrequencyUnit());
      var f = cron.scheduler.scheduleAtFixedRate(job, job.getDelay(), job.getFrequency(), job.getFrequencyUnit());
      cron.futures.add(f);
    }
    return cron;
  }

  private CronExecutor() {
    scheduler = Executors.newScheduledThreadPool(1,
      new NamedThreadFactory(THREAD_NAME, Thread.NORM_PRIORITY, true)
    );
  }

  @Override
  public void stop() throws Exception {
    LOG.info("Stopping cron executor with {} jobs", futures.size());
    scheduler.shutdown();
    for (var f : futures) {
      f.cancel(true);
    }
    LOG.info("Cron executor stopped");
  }
}
