package life.catalogue.concurrent;

import life.catalogue.api.vocab.JobStatus;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import life.catalogue.common.Idle;

import life.catalogue.common.Managed;

import org.simplejavamail.api.email.Email;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.email.EmailBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The background job executor using a priority ordered queue.
 * It supports notification of errors via email and blocking jobs that depend on a locked access to a single dataset.
 */
public class JobExecutor implements Managed, Idle {
  private static final Logger LOG = LoggerFactory.getLogger(JobExecutor.class);

  private final JobConfig cfg;
  private final PriorityBlockingQueue<Runnable> queue;
  private final ConcurrentMap<UUID, ComparableFutureTask> futures = new ConcurrentHashMap<>();
  private final Mailer mailer;
  private final String onErrorTo;
  private final String onErrorFrom;
  private ColExecutor exec;

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

  public JobExecutor(JobConfig cfg) throws Exception {
    this(cfg, null);
  }

  public JobExecutor(JobConfig cfg, @Nullable Mailer mailer) throws Exception {
    LOG.info("Created new job executor with {} workers and a queue size of {}", cfg.threads, cfg.queue);
    this.cfg = cfg;
    queue = new PriorityBlockingQueue<>(cfg.queue);
    this.mailer = mailer;
    this.onErrorTo = cfg.onErrorTo;
    this.onErrorFrom = cfg.onErrorFrom;
    start();
  }

  class ColExecutor extends ThreadPoolExecutor {
    public ColExecutor(JobConfig cfg, PriorityBlockingQueue queue) {
      super(cfg.threads, cfg.threads, 60L, TimeUnit.SECONDS, queue,
        new NamedThreadFactory("background-worker"),
        new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
      super.afterExecute(r, t);
      // no check as we cannot submit any other jobs
      BackgroundJob job = ((ComparableFutureTask) r).task;
      try {
        futures.remove(job.getKey()).get();
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
      if (job.getStatus() == JobStatus.FAILED) {
        sendErrorMail(job);
      }
    }
  }

  private void sendErrorMail(BackgroundJob job){
    if (mailer != null && onErrorTo != null && onErrorFrom != null) {
      StringWriter sw = new StringWriter();
      sw.write(job.getClass().getSimpleName() + " job "+job.getKey());
      sw.write(" has failed with an exception");
      if (job.getError() != null) {
        sw.write(" " + job.getError().getClass().getSimpleName()+":\n\n");
        PrintWriter pw = new PrintWriter(sw);
        job.getError().printStackTrace(pw);
      } else {
        sw.write(".\n");
      }

      Email mail = EmailBuilder.startingBlank()
        .to(onErrorTo)
        .from(onErrorFrom)
        .withSubject(String.format("COL job %s %s failed", job.getClass().getSimpleName(), job.getKey()))
        .withPlainText(sw.toString())
        .buildEmail();
      mailer.sendMail(mail, true);
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
    // look for duplicates in the queue
    for (BackgroundJob qj : getQueue()) {
      if (job.isDuplicate(qj)) {
        throw new IllegalArgumentException("An identical job is queued already");
      }
    }
    LOG.info("Scheduling new {} job {} by user {}", job.getJobName(), job.getKey(), job.getUserKey());
    var ftask = new ComparableFutureTask(job);
    futures.put(job.getKey(), ftask);
    exec.execute(ftask);
  }
  
}

