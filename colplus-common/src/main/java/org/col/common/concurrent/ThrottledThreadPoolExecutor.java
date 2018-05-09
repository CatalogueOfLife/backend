package org.col.common.concurrent;

import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Throttling Task submission rate using ThreadPoolExecutor and Semaphore
 * 1) Before executing a task a lock in semaphore is requested
 * 2) When lock is acquired execution works normally and the task is placed on the executors queue
 * 3) Once task is completed; lock is released to semaphore
 */
public class ThrottledThreadPoolExecutor extends ThreadPoolExecutor {
  private static final Logger LOG = LoggerFactory.getLogger(ThrottledThreadPoolExecutor.class);
  private final Semaphore semaphore;

  public static ThrottledThreadPoolExecutor newFixedThreadPool(int nThreads, int queueCapacity) {
    return new ThrottledThreadPoolExecutor(nThreads, nThreads, queueCapacity, 0L, TimeUnit.MILLISECONDS);
  }

  public ThrottledThreadPoolExecutor(int corePoolSize, int maximumPoolSize, int queueCapacity,
                                     long keepAliveTime, TimeUnit unit) {
    super(corePoolSize, maximumPoolSize, keepAliveTime, unit, new LinkedBlockingQueue<Runnable>(queueCapacity));
    semaphore = new Semaphore(queueCapacity);
  }

  @Override
  public void execute(final Runnable task) {
    try {
      semaphore.acquire();
      super.execute(task);
    } catch (final InterruptedException e) {
      LOG.warn("Interrupted thread while waiting for tasks to put on the full queue", e);
      throw new RejectedExecutionException();
    }
  }

  @Override
  protected void afterExecute(Runnable r, Throwable t) {
    super.afterExecute(r, t);
    if (t != null) {
      LOG.error("Error after executing task {}", r.getClass().getSimpleName(), t);
    }
    semaphore.release();
  }
}