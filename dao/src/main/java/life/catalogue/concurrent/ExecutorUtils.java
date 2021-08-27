package life.catalogue.concurrent;

import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ExecutorUtils {
  private static final Logger LOG = LoggerFactory.getLogger(ExecutorUtils.class);
  
  // milliseconds to wait during shutdown before forcing a shutdown
  public static final int MILLIS_TO_DIE = 12000;


  public static ExecutorService newCachedThreadPool(int maximumPoolSize, ThreadFactory threadFactory) {
    return newCachedThreadPool(maximumPoolSize,2*maximumPoolSize, threadFactory);
  }

  /**
   * Creates a thread pool that creates new threads as needed up to the given maximum, but
   * will reuse previously constructed threads when they are available, and uses the provided
   * ThreadFactory to create new threads when needed. A blocking queue of given size is used, but otherwise
   * additional tasks are executed in the caller thread and never discarded or rejected.
   *
   * @param threadFactory the factory to use when creating new threads
   * @param maximumPoolSize maximum number of thread to create in the pool
   * @param queueSize size of the queue before the calling thread will run submitted tasks
   * @return the newly created thread pool
   */
  public static ExecutorService newCachedThreadPool(int maximumPoolSize, int queueSize, ThreadFactory threadFactory) {
    return new ThreadPoolExecutor(0, maximumPoolSize,
      30L, TimeUnit.SECONDS,
      new LinkedBlockingQueue<>(queueSize),
      threadFactory, new ThreadPoolExecutor.CallerRunsPolicy());
  }

  /**
   * Shutdown executor and wait until all jobs are done no matter how long it takes.
   * (actually waits for one month at most).
   */
  public static void shutdown(ExecutorService exec) {
    shutdown(exec, 31, TimeUnit.DAYS);
  }
  
  public static void shutdown(ExecutorService exec, int timeout, TimeUnit unit) {
    try {
      LOG.info("attempt to shutdown executor within {} {}", timeout, unit);
      exec.shutdown();
      if (exec.awaitTermination(timeout, unit)) {
        LOG.info("shutdown succeeded orderly");
      } else {
        forceShutdown(exec);
      }
      
    } catch (InterruptedException e) {
      LOG.info("executor shutdown interrupted, force immediate shutdown");
      forceShutdown(exec);
    }
  }
  
  private static void forceShutdown(ExecutorService exec) {
    int count = exec.shutdownNow().size();
    LOG.warn("forced shutdown, discarding {} queued tasks", count);
  }
}
