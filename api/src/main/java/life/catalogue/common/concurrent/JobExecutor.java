package life.catalogue.common.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ThreadPool with a priority blocking queue which is partially exposed through this class.
 */
public class JobExecutor implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(JobExecutor.class);

  private final ThreadPoolExecutor exec;
  private final PriorityBlockingQueue<Runnable> queue;
  private final ConcurrentMap<UUID, ComparableFutureTask> futures = new ConcurrentHashMap<>();

  static class ComparableFutureTask extends FutureTask<Void> implements Runnable, Comparable<ComparableFutureTask> {

    private final BackgroundJob task;

    public Runnable getTask() {
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

  public JobExecutor(int poolSize) {
    this(poolSize, 1000);
  }

  public JobExecutor(int poolSize, int queueSize) {
    this("job-worker", poolSize, queueSize);
  }

  public JobExecutor(String poolName, int poolSize, int queueSize) {
    LOG.info("Created new job executor with {} workers and a queue size of {}", poolSize, queueSize);
    queue = new PriorityBlockingQueue<>(queueSize);
    exec = new ThreadPoolExecutor(poolSize, poolSize, 60L, TimeUnit.SECONDS, queue, new NamedThreadFactory(poolName), new ThreadPoolExecutor.AbortPolicy());
    exec.allowCoreThreadTimeOut(true);
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
    ComparableFutureTask f = futures.get(key);
    if (f != null) {
      return f.task;
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

  public void submit(BackgroundJob task) {
    if (task == null) {
      throw new NullPointerException();
    }
    // look for duplicates in the queue
    for (BackgroundJob qj : getQueue()) {
      if (task.isDuplicate(qj)) {
        throw new IllegalArgumentException("A duplicate job is queued already");
      }
    }
    task.setHandler(this::onFinished);
    ComparableFutureTask ftask = new ComparableFutureTask(task);
    exec.execute(ftask);
    futures.put(task.getKey(), ftask);
  }

  void onFinished(BackgroundJob job){
    futures.remove(job.getKey());
  }

  public void close() {
    ExecutorUtils.shutdown(exec, ExecutorUtils.MILLIS_TO_DIE, TimeUnit.MILLISECONDS);
  }
  
}

