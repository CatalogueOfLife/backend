package life.catalogue.concurrent;

import life.catalogue.api.exception.TooManyRequestsException;
import life.catalogue.api.model.JobInfo;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.common.Idle;
import life.catalogue.common.Managed;
import life.catalogue.common.collection.CountMap;
import life.catalogue.dao.JobDao;
import life.catalogue.dao.UserCrudDao;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

/**
 * The background job executor with several priority ordered queues, one per JobLane.
 * Each lane has its own worker pool so e.g. long running imports cannot starve regular background jobs.
 * Within a lane jobs of equal priority are executed in submission order.
 *
 * Jobs returning a serial key via getSerialBy() are additionally serialized within their lane:
 * only one job per serial key runs at any time, in submission order. This is used by sector syncs
 * which may run in parallel across projects but never within the same project.
 *
 * The executor supports notification of errors via email and blocking jobs that depend on a locked access to a single dataset.
 * If a JobDao is given, every job is persisted to the job table with all its status changes.
 */
public class JobExecutor implements Managed, Idle, SomeExecutor {
  private static final Logger LOG = LoggerFactory.getLogger(JobExecutor.class);
  private static final String METRIC_GROUP_NAME = "jobs";

  private final JobConfig cfg;
  private final Map<JobLane, PriorityBlockingQueue<Runnable>> queues = new EnumMap<>(JobLane.class);
  private final ConcurrentMap<UUID, ComparableFutureTask> futures = new ConcurrentHashMap<>();
  private final SerialGate gate = new SerialGate();
  private final UserCrudDao udao;
  private final @Nullable EmailNotification emailer;
  private final @Nullable JobDao jobDao; // optional - without it jobs are not persisted, e.g. in CLI tools
  private Map<JobLane, ColExecutor> execs;
  private List<JobInfo> staleJobs = List.of(); // jobs cancelled at startup as they never survived the last server run
  private final Timer timer;

  @Override
  public void start() throws Exception {
    if (execs == null) {
      if (jobDao != null) {
        // jobs that were waiting or running when the last server stopped can never finish
        staleJobs = jobDao.cancelStale();
      }
      execs = new EnumMap<>(JobLane.class);
      for (JobLane lane : JobLane.values()) {
        var exec = new ColExecutor(lane, cfg.threads(lane), queues.get(lane));
        exec.allowCoreThreadTimeOut(true);
        execs.put(lane, exec);
      }
    }
  }

  @Override
  public void stop() throws Exception {
    if (execs != null) {
      for (var exec : execs.values()) {
        ExecutorUtils.shutdown(exec, ExecutorUtils.MILLIS_TO_DIE, TimeUnit.MILLISECONDS);
      }
      execs = null;
    }
  }

  @Override
  public boolean hasStarted() {
    return execs != null;
  }

  /**
   * @return the persisted jobs that were cancelled when this executor started,
   * as they were still waiting or running when the last server stopped.
   */
  public List<JobInfo> getStaleJobs() {
    return staleJobs;
  }

  static class ComparableFutureTask extends FutureTask<Void> implements Runnable, Comparable<ComparableFutureTask> {
    private static final AtomicLong SEQ = new AtomicLong();

    private final BackgroundJob task;
    // monotonic submission sequence as a FIFO tie breaker for jobs of equal priority
    private final long seq = SEQ.incrementAndGet();

    public BackgroundJob getTask() {
      return task;
    }

    ComparableFutureTask(BackgroundJob runnable) {
      super(runnable, null);
      this.task = runnable;
    }

    @Override
    public int compareTo(ComparableFutureTask o) {
      int prio = this.task.getPriority().compareTo(o.task.getPriority());
      return prio != 0 ? prio : Long.compare(this.seq, o.seq);
    }
  }

  /**
   * Serializes jobs sharing the same serial key.
   * Only one job per key is ever handed to the worker pools, all others are parked here in submission order
   * and released one by one as the active job of their key finishes.
   */
  private static class SerialGate {
    private static class Group {
      ComparableFutureTask active;
      final Deque<ComparableFutureTask> parked = new ArrayDeque<>();
    }

    private final Map<Object, Group> groups = new HashMap<>();

    /**
     * @return true if the task may be executed right away, false if it was parked behind the keys active job
     */
    synchronized boolean tryAcquire(Object key, ComparableFutureTask task) {
      Group g = groups.get(key);
      if (g == null) {
        g = new Group();
        g.active = task;
        groups.put(key, g);
        return true;
      }
      g.parked.add(task);
      return false;
    }

    /**
     * Releases the key if the given task is its active job.
     * @return the next parked task to execute, which becomes the keys new active job. Null if nothing is parked.
     */
    synchronized ComparableFutureTask release(Object key, ComparableFutureTask task) {
      Group g = groups.get(key);
      if (g == null || g.active != task) {
        return null; // released before, e.g. via cancel
      }
      var next = g.parked.poll();
      if (next == null) {
        groups.remove(key);
      } else {
        g.active = next;
      }
      return next;
    }

    /**
     * Removes a parked task, e.g. when it gets cancelled.
     * @return true if the task was parked and has been removed
     */
    synchronized boolean removeParked(ComparableFutureTask task) {
      Group g = groups.get(task.getTask().getSerialBy());
      return g != null && g.parked.remove(task);
    }

    synchronized List<ComparableFutureTask> parked() {
      return groups.values().stream()
        .flatMap(g -> g.parked.stream())
        .collect(Collectors.toList());
    }

    synchronized boolean isEmpty() {
      return groups.isEmpty();
    }
  }

  public JobExecutor(JobConfig cfg, MetricRegistry registry, @Nullable EmailNotification emailer, UserCrudDao udao, @Nullable JobDao jobDao) throws Exception {
    LOG.info("Created new job executor with lanes default={}/{}, import={}/{}, sync={}/{} (threads/queue)",
      cfg.threads, cfg.queue, cfg.importThreads, cfg.importQueue, cfg.syncThreads, cfg.syncQueue);
    this.cfg = cfg;
    this.udao = udao;
    this.jobDao = jobDao;
    for (JobLane lane : JobLane.values()) {
      queues.put(lane, new PriorityBlockingQueue<>(cfg.queueSize(lane)));
    }
    if (emailer == null) {
      LOG.warn("No emailer configured!");
    }
    if (jobDao == null) {
      LOG.warn("No job dao configured, jobs will not be persisted!");
    }
    this.emailer = emailer;
    // track metrics e.g. queue sizes
    for (JobLane lane : JobLane.values()) {
      final var q = queues.get(lane);
      registry.register(MetricRegistry.name(JobExecutor.class, METRIC_GROUP_NAME, "queue", lane.name().toLowerCase()), (Gauge<Integer>) q::size);
    }
    timer = registry.register(MetricRegistry.name(JobExecutor.class, METRIC_GROUP_NAME, "duration"), new Timer());
    // start up
    start();
  }

  class ColExecutor extends ThreadPoolExecutor {

    public ColExecutor(JobLane lane, int threads, PriorityBlockingQueue<Runnable> queue) {
      super(threads, threads, 60L, TimeUnit.SECONDS, queue,
        new NamedThreadFactory("background-worker-" + lane.name().toLowerCase()),
        new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
      // no check as we cannot submit any other jobs
      ComparableFutureTask ftask = (ComparableFutureTask) r;
      BackgroundJob job = ftask.task;
      if (t != null) {
        // what shall we do with this? Do throwables ever reach here?
        // or are they the same as wrapped with ExecutionException below???
        LOG.error("Job {} failed with {}", job.getKey(), t.getMessage(), t);
      }

      releaseSerialAndPromote(job, ftask);

      try {
        var f = futures.remove(job.getKey());
        if (f != null) {
          f.get(); //TODO: what for???
        } else {
          LOG.warn("Job {} has no future to remove", job.getKey());
        }
      } catch (InterruptedException e) {
        // ignore
      } catch (CancellationException e) {
        // the task was cancelled while queued but slipped past purge - nothing to do
      } catch (ExecutionException e) {
        // resubmit blocked jobs. DatasetBlockingJob implements some waiting rules based on number of attempts
        if (e.getCause() instanceof DatasetBlockedException && job instanceof DatasetBlockingJob) {
          DatasetBlockedException be = (DatasetBlockedException) e.getCause();
          DatasetBlockingJob bj = (DatasetBlockingJob) job;
          LOG.info("Resubmit job {} #{} which requires a lock on dataset {} and is blocked by {}", job.getKey(), bj.getRetry(), be.datasetKey, be.blockedBy);
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

  /**
   * Releases the serial key of an ended job and executes the next job parked behind it, if any.
   */
  private void releaseSerialAndPromote(BackgroundJob job, ComparableFutureTask ftask) {
    Object serial = job.getSerialBy();
    if (serial != null) {
      var next = gate.release(serial, ftask);
      if (next != null) {
        LOG.info("Unpark job {} serialized by {}", next.task.getKey(), serial);
        execs.get(next.task.getLane()).execute(next);
      }
    }
  }

  public int queueSize() {
    return queues.values().stream().mapToInt(PriorityBlockingQueue::size).sum() + gate.parked().size();
  }

  public int queueSize(JobLane lane) {
    return queues.get(lane).size() + (int) gate.parked().stream().filter(t -> t.task.getLane() == lane).count();
  }

  /**
   * @return number of queued jobs by lane, including parked serialized jobs
   */
  public Map<JobLane, Integer> queueSizes() {
    Map<JobLane, Integer> sizes = new EnumMap<>(JobLane.class);
    for (JobLane lane : JobLane.values()) {
      sizes.put(lane, queueSize(lane));
    }
    return sizes;
  }

  /**
   * @return true if all queues are empty
   */
  public boolean hasEmptyQueue() {
    return queues.values().stream().allMatch(PriorityBlockingQueue::isEmpty) && gate.isEmpty();
  }

  /**
   * @return true if the executor has no actively running threads and all queues are empty
   */
  @Override
  public boolean isIdle() {
    return !hasStarted() || hasEmptyQueue() && execs.values().stream().allMatch(ex -> ex.getActiveCount() == 0);
  }

  public BackgroundJob cancel (UUID key, int user) {
    ComparableFutureTask f = futures.remove(key);
    if (f != null) {
      BackgroundJob job = f.task;
      LOG.info("Canceled job {} by user {}", job.getKey(), user);
      // a job that is actually executing is interrupted and finishes via run(),
      // marking itself CANCELED. A job that never produced output - still queued
      // (WAITING) or blocked on a dataset lock (BLOCKED) - won't reach run() cleanly,
      // so mark and clean it up explicitly here.
      final JobStatus st = job.getStatus();
      f.cancel(true);
      if (st == JobStatus.WAITING || st == JobStatus.BLOCKED) {
        if (job.getSerialBy() == null || !gate.removeParked(f)) {
          // the job sat in an executor queue: remove it and release its serial key if it held one
          execs.get(job.getLane()).purge();
          releaseSerialAndPromote(job, f);
        }
        job.setStatus(JobStatus.CANCELED);
        job.onCancelBeforeStart();
        job.persist();
      }
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

  /**
   * Checks if a job with the given key is currently queued or running.
   * @param key
   * @return
   */
  public boolean exists(UUID key) {
    return futures.containsKey(key);
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
    List<BackgroundJob> queued = new ArrayList<>();
    for (var q : queues.values()) {
      q.forEach(x -> queued.add(((ComparableFutureTask) x).task));
    }
    gate.parked().forEach(t -> queued.add(t.task));
    return Stream.concat(
      futures.values().stream()
             .map(f -> f.task)
             .filter(BackgroundJob::isRunning),
      queued.stream()
    );
  }

  @Override
  public void submit(BackgroundJob job) {
    if (job == null) {
      throw new NullPointerException();
    }
    assertOnline();
    final JobLane lane = job.getLane();
    if (queueSize(lane) >= cfg.queueSize(lane)) {
      throw new TooManyRequestsException("The " + lane + " job queue is full, please try again later");
    }
    // look for duplicates in the queue and count by user
    var jobCnt = new CountMap<Class<?>>();
    for (BackgroundJob qj : getQueue()) {
      if (job.isDuplicate(qj)) {
        throw new IllegalArgumentException("An identical job " + qj.getKey() + " is queued already");
      }
      if (qj.getUserKey() == job.getUserKey()) {
        jobCnt.inc(job.maxPerUserClass());
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
    if (!user.isAdmin() && jobCnt.getOrDefault(job.maxPerUserClass(), 0) >= jobUserLimit(job)) {
      throw new TooManyRequestsException(user.getUsername() + " already runs the maximum allowed number of " + job.getClass().getSimpleName() + " jobs");
    }

    LOG.info("Scheduling new {} job {} by user {}: {}<{}>", job.getJobName(), job.getKey(), job.getUserKey(), user.getName(), user.getEmail());
    job.setUser(user);
    job.setEmailer(emailer);
    job.setTimer(timer);
    job.setCfg(cfg);
    final boolean newJob = persistSubmission(job);
    var ftask = new ComparableFutureTask(job);
    futures.put(job.getKey(), ftask);
    try {
      Object serial = job.getSerialBy();
      if (serial != null && !gate.tryAcquire(serial, ftask)) {
        LOG.info("Park {} job {} behind the running job serialized by {}", job.getJobName(), job.getKey(), serial);
      } else {
        execs.get(lane).execute(ftask);
      }
    } catch (RuntimeException e) {
      futures.remove(job.getKey());
      if (newJob && jobDao != null) {
        jobDao.delete(job.getKey());
      }
      throw e;
    }
  }

  /**
   * Writes the job to the job table when it is first submitted.
   * Blocked jobs being resubmitted already have their record and only get updated.
   * @return true if a new job record was created
   */
  private boolean persistSubmission(BackgroundJob job) {
    if (jobDao != null) {
      job.setPersister(jobDao::update);
      if (job.getStatus() == JobStatus.WAITING) {
        jobDao.create(job);
        return true;
      } else {
        job.persist();
      }
    }
    return false;
  }

  private int jobUserLimit(BackgroundJob job) {
    return cfg.userLimit.getOrDefault(job.getClass().getSimpleName(), Integer.MAX_VALUE);
  }

}
