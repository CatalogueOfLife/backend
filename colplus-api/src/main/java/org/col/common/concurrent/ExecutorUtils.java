package org.col.common.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ExecutorUtils {
  private static final Logger LOG = LoggerFactory.getLogger(ExecutorUtils.class);
  
  /**
   * Shutdown executor with a 10s timeout.
   */
  public static void shutdown(ExecutorService exec) {
    shutdown(exec, 10, TimeUnit.SECONDS);
  }
  
  public static void shutdown(ExecutorService exec, int timeout, TimeUnit unit) {
    try {
      LOG.debug("attempt to shutdown executor within {} {}", timeout, unit);
      exec.shutdown();
      if (exec.awaitTermination(timeout, unit)) {
        LOG.debug("shutdown succeeded orderly");
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
