package life.catalogue.concurrent;

import life.catalogue.api.vocab.JobStatus;
import org.simplejavamail.api.email.Email;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.email.EmailBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The background job executor using a priority ordered queue.
 * It supports notification of errors via email and blocking jobs that depend on a locked access to a single dataset.
 */
public class JobExecutor implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(JobExecutor.class);

  private final ColExecutor exec;
  private final PriorityBlockingQueue<Runnable> queue;
  private final ConcurrentMap<UUID, ComparableFutureTask> futures = new ConcurrentHashMap<>();
  private final Mailer mailer;
  private final String onErrorTo;
  private final String onErrorFrom;

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

  public JobExecutor(JobConfig cfg) {
    this(cfg, null);
  }

  public JobExecutor(JobConfig cfg, @Nullable Mailer mailer) {
    LOG.info("Created new job executor with {} workers and a queue size of {}", cfg.threads, cfg.queue);
    queue = new PriorityBlockingQueue<>(cfg.queue);
    exec = new ColExecutor(cfg, queue);
    exec.allowCoreThreadTimeOut(true);
    this.mailer = mailer;
    this.onErrorTo = cfg.onErrorTo;
    this.onErrorFrom = cfg.onErrorFrom;
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
   * @return true if the executor has actively running threads or the queue is not empty
   */
  public boolean isActive() {
    return !hasEmptyQueue() && exec.getActiveCount() > 0;
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

  public List<BackgroundJob> getQueue() {
    return Stream.concat(
      futures.values().stream()
        .map(f -> f.task)
        .filter(BackgroundJob::isRunning),
      queue.stream()
        .map(x -> ((ComparableFutureTask)x).task)
    ).collect(Collectors.toList());
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
    var ftask = new ComparableFutureTask(job);
    futures.put(job.getKey(), ftask);
    exec.execute(ftask);
  }

  public void close() {
    ExecutorUtils.shutdown(exec, ExecutorUtils.MILLIS_TO_DIE, TimeUnit.MILLISECONDS);
  }
  
}

