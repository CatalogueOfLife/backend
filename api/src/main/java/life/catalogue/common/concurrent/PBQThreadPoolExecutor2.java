package life.catalogue.common.concurrent;

import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * ThreadPool with a priority blocking queue which is partially exposed through this class.
 */
public class PBQThreadPoolExecutor2 {
  private final ThreadPoolExecutor exec;
  private final PriorityBlockingQueue<Runnable> queue;

  public class ComparableFutureTask extends FutureTask<Void> implements Runnable, Comparable<ComparableFutureTask> {

    private Runnable task;
    private boolean priority;

    public Runnable getTask() {
      return task;
    }

    public boolean isPriority() {
      return priority;
    }

    public void setPriority(boolean priority) {
      this.priority = priority;
    }

    ComparableFutureTask(Runnable runnable, boolean priority) {
      super(runnable, null);
      this.priority = priority;
      this.task = runnable;
    }

    @Override
    public int compareTo(ComparableFutureTask o) {
      return -1 * Boolean.compare(this.priority, o.priority);
    }

  }

  public PBQThreadPoolExecutor2(int poolSize, int queueSize) {
    this(poolSize, 60L, TimeUnit.SECONDS,
        new PriorityBlockingQueue<>(queueSize),
        Executors.defaultThreadFactory(),
        new ThreadPoolExecutor.AbortPolicy());
  }

  public PBQThreadPoolExecutor2(int poolSize,
                                long keepAliveTime,
                                TimeUnit unit,
                                PriorityBlockingQueue<Runnable> workQueue,
                                ThreadFactory threadFactory,
                                RejectedExecutionHandler handler) {
    queue = workQueue;
    exec = new ThreadPoolExecutor(poolSize, poolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
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
  
  public boolean isQueued(ComparableFutureTask f) {
    return queue.contains(f);
  }

  /**
   *
   */
  public List<Runnable> getQueue() {
    return queue.stream()
        .map(x -> ((ComparableFutureTask)x).task)
        .collect(Collectors.toList());
  }
  
  public void purge() {
    exec.purge();
  }
  
  public ComparableFutureTask submit(Runnable task, boolean priority) {
    if (task == null)
      throw new NullPointerException();
    ComparableFutureTask ftask = new ComparableFutureTask(task, priority);
    exec.execute(ftask);
    return ftask;
  }
  
  public void stop() {
    ExecutorUtils.shutdown(exec, ExecutorUtils.MILLIS_TO_DIE, TimeUnit.MILLISECONDS);
  }
  
}

