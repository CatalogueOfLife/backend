package org.col.util.concurrent;

import java.util.concurrent.*;

/**
 * Throttling Task submission rate using ThreadPoolExecutor and Semaphore
 * A Semaphore with a number which must be equal to maximum number of tasks in blocking queue at any given point of time.
 * The approach works like this:
 * 1) Before executing a task a lock in semaphore is requested
 * 2) If lock is acquired then execution works normally; Otherwise retry will happen until lock is acquired
 * 3) Once task is completed; lock is released to semaphore
 */
public class BlockingThreadPoolExecutor extends ThreadPoolExecutor {
  private final Semaphore semaphore;

  public static BlockingThreadPoolExecutor newFixedThreadPool(int nThreads, int queueCapacity) {
    return new BlockingThreadPoolExecutor(nThreads, nThreads,
        0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<Runnable>(queueCapacity));
  }

  public BlockingThreadPoolExecutor(int corePoolSize, int maximumPoolSize,
                                    long keepAliveTime, TimeUnit unit,
                                    BlockingQueue<Runnable> workQueue) {
    super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    semaphore = new Semaphore(corePoolSize + 50);
  }

  @Override
  protected void beforeExecute(Thread t, Runnable r) {
    super.beforeExecute(t, r);
  }

  @Override
  public void execute(final Runnable task) {
    boolean acquired = false;
    do {
      try {
        semaphore.acquire();
        acquired = true;
      } catch (final InterruptedException e) {
        //LOGGER.warn("InterruptedException whilst aquiring semaphore", e);
      }
    } while (!acquired);
    try {
      super.execute(task);
    } catch (final RejectedExecutionException e) {
      System.out.println("Task Rejected");
      semaphore.release();
      throw e;
    }
  }

  @Override
  protected void afterExecute(Runnable r, Throwable t) {
    super.afterExecute(r, t);
    if (t != null) {
      t.printStackTrace();
    }
    semaphore.release();
  }
}