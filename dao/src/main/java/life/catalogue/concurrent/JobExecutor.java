package life.catalogue.concurrent;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import life.catalogue.api.exception.TooManyRequestsException;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.common.Idle;
import life.catalogue.common.Managed;
import life.catalogue.dao.UserDao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.ws.rs.NotAllowedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

/**
 * The background job executor using a priority ordered queue.
 * It supports notification of errors via email and blocking jobs that depend on a locked access to a single dataset.
 */
public class JobExecutor implements Managed, Idle {
  private static final Logger LOG = LoggerFactory.getLogger(JobExecutor.class);
  private static final String METRIC_GROUP_NAME = "jobs";

  private final JobConfig cfg;
  private final PriorityBlockingQueue<Runnable> queue;
  private final ConcurrentMap<UUID, ComparableFutureTask> futures = new ConcurrentHashMap<>();
  private final UserDao udao;
  private final @Nullable EmailNotification emailer;
  private ColExecutor exec;
  private final Timer timer;

  @Override
  public void start() throws Exception {
    if (exec == null) {
      exec = new ColExecutor(cfg, queue);
      exec.allowCoreThreadTimeOut(true);
    }
  }

  @Override
  public void stop() throws Exception {
    if (exec != null) {
      ExecutorUtils.shutdown(exec, ExecutorUtils.MILLIS_TO_DIE, TimeUnit.MILLISECONDS);
      exec = null;
    }
  }

  @Override
  public boolean hasStarted() {
    return exec != null;
  }

  static class ComparableFutureTask extends FutureTask<Void> implements Runnable, Comparable<ComparableFutureTask> {

    private final BackgroundJob task;

    public BackgroundJob getTask() {
      return task;
    }

    ComparableFutureTask(BackgroundJob runnable) {
      super(runnable, null);
      this.task = runnable;
    }

    @Override
    public int compareTo(ComparableFutureTask o) {
      return this.task.getPriority().compareTo(o.task.getPriority());
    }
  }

  public JobExecutor(JobConfig cfg, MetricRegistry registry, @Nullable EmailNotification emailer, UserDao uDao) throws Exception {
    LOG.info("Created new job executor with {} workers and a queue size of {}", cfg.threads, cfg.queue);
    this.cfg = cfg;
    this.udao = uDao;
    queue = new PriorityBlockingQueue<>(cfg.queue);
    if (emailer == null) {
      LOG.warn("No emailer configured!");
    }
    this.emailer = emailer;
    // track metrics e.g. queue size
    registry.register(MetricRegistry.name(JobExecutor.class, METRIC_GROUP_NAME, "queue"), (Gauge<Integer>) queue::size);
    timer = registry.register(MetricRegistry.name(JobExecutor.class, METRIC_GROUP_NAME, "duration"), new Timer());
    // start up
    start();
  }

  class ColExecutor extends ThreadPoolExecutor {
    public ColExecutor(JobConfig cfg, PriorityBlockingQueue<Runnable> queue) {
      super(cfg.threads, cfg.threads, 60L, TimeUnit.SECONDS, queue,
        new NamedThreadFactory("background-worker"),
        new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
      // no check as we cannot submit any other jobs
      BackgroundJob job = ((ComparableFutureTask) r).task;
      if (t != null) {
        // what shall we do with this? Do throwables ever reach here?
        // or are they the same as wrapped with ExecutionException below???
        LOG.error("Job {} failed with {}", job.getKey(), t.getMessage(), t);
      }

      try {
        var f = futures.remove(job.getKey());
        if (f != null) {
          f.get(); //TODO: what for???
        } else {
          LOG.warn("Job {} has no future to remove", job.getKey());
        }
      } catch (InterruptedException e) {
        // ignore
      } catch (ExecutionException e) {
        // resubmit blocked jobs. DatasetBlockingJob implements some waiting rules based on number of attempts
        if (e.getCause() instanceof DatasetBlockedException && job instanceof DatasetBlockingJob) {
          DatasetBlockedException be = (DatasetBlockedException) e.getCause();
          DatasetBlockingJob bj = (DatasetBlockingJob) job;
          LOG.info("Resubmit job {} #{} which requires a lock on dataset {} and is blocked by {}", job.getKey(), bj.getAttempt(), be.datasetKey, be.blockedBy);
          JobExecutor.this.submit(job);
        }
      }
      // mail on error if configured
      if (emailer != null && job.getStatus() == JobStatus.FAILED) {
        try {
          emailer.sendErrorMail(job);
        } catch (Exception e) {
          LOG.error("Failed to send error email for job {}", job.getKey(), e);
        }
      }
    }
  }

  public int queueSize() {
    return queue.size();
  }
  
  /**
   * @return true if queue is empty
   */
  public boolean hasEmptyQueue() {
    return queue.isEmpty();
  }

  /**
   * @return true if the executor has no actively running threads and the queue is empty
   */
  @Override
  public boolean isIdle() {
    return !hasStarted() || hasEmptyQueue() && exec.getActiveCount() == 0;
  }

  public BackgroundJob cancel (UUID key, int user) {
    ComparableFutureTask f = futures.remove(key);
    if (f != null) {
      BackgroundJob job = f.task;
      LOG.info("Canceled job {} by user {}", job.getKey(), user);
      f.cancel(true);
      exec.purge();
      return job;
    }
    return null;
  }

  public BackgroundJob getJob(UUID key) {
    if (key!=null) {
      ComparableFutureTask f = futures.get(key);
      if (f != null) {
        return f.task;
      }
    }
    return null;
  }

  public boolean isQueued(UUID key) {
    BackgroundJob job = getJob(key);
    return job != null;
  }

  public boolean isQueued(ComparableFutureTask f) {
    return queue.contains(f);
  }

  public <T extends BackgroundJob> List<T> getQueueByJobClass(Class<T> jobClass) {
    return getQueueStream()
      .filter(jobClass::isInstance)
      .map(job -> (T) job)
      .collect(Collectors.toList());
  }

  public List<DatasetBlockingJob> getQueueByDataset(int datasetKey) {
    return getQueueStream()
      .filter(DatasetBlockingJob.class::isInstance)
      .map(DatasetBlockingJob.class::cast)
      .filter(job -> job.datasetKey==datasetKey)
      .collect(Collectors.toList());
  }

  public List<BackgroundJob> getQueue() {
    return getQueueStream().collect(Collectors.toList());
  }

  private Stream<BackgroundJob> getQueueStream() {
    return Stream.concat(
      futures.values().stream()
             .map(f -> f.task)
             .filter(BackgroundJob::isRunning),
      queue.stream()
           .map(x -> ((ComparableFutureTask)x).task)
    );
  }

  public void submit(BackgroundJob job) {
    if (job == null) {
      throw new NullPointerException();
    }
    assertOnline();
    // look for duplicates in the queue and count by user
    var byUser = new Int2IntOpenHashMap();
    for (BackgroundJob qj : getQueue()) {
      if (job.isDuplicate(qj)) {
        throw new IllegalArgumentException("An identical job " + qj.getKey() + " is queued already");
      }
      if (byUser.containsKey(job.getUserKey())) {
        byUser.put(job.getUserKey(), byUser.get(job.getUserKey())+1);
      } else {
        byUser.put(job.getUserKey(), 1);
      }
    }
    // make sure all components needed for the job have started before we even submit the job
    job.assertComponentsOnline();
    // check user
    User user = udao.get(job.getUserKey());
    if (user == null) {
      throw new IllegalArgumentException("No user "+job.getUserKey()+" existing");
    }
    // are number of jobs per user restricted?
    if (job.maxPerUser() > 0 && byUser.getOrDefault(job.getUserKey(), 0) >= job.maxPerUser()) {
      throw new TooManyRequestsException(user.getUsername() + " already runs the maximum allowed number of " + job.getClass().getSimpleName() + " jobs");
    }

    LOG.info("Scheduling new {} job {} by user {}: {}<{}>", job.getJobName(), job.getKey(), job.getUserKey(), user.getName(), user.getEmail());
    job.setUser(user);
    job.setEmailer(emailer);
    job.setTimer(timer);
    job.setCfg(cfg);
    var ftask = new ComparableFutureTask(job);
    futures.put(job.getKey(), ftask);
    exec.execute(ftask);
  }
  
}

