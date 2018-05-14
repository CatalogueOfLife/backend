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
      LOG.warn("attempt to shutdown executor within 10s");
      exec.shutdown();
      exec.awaitTermination(timeout, unit);

    } catch (InterruptedException e) {
      LOG.error("executor interrupted");

    } finally {
      if (!exec.isTerminated()) {
        LOG.warn("cancel non-finished tasks");
      }
      exec.shutdownNow();
      LOG.info("shutdown finished");
    }
  }
}
