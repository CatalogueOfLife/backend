package life.catalogue.concurrent;

import life.catalogue.common.Managed;
import life.catalogue.common.util.LoggingUtils;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Skeleton for the background schedulers that poll the database for outstanding work and submit jobs for it.
 *
 * The rule these exist to enforce: a scheduler is the only thread that ever submits its kind of work, so a
 * failed submission is a reason to back off, never a reason to end. Both schedulers used to treat any
 * Exception as fatal and set running=false, which meant a stopped or full job executor - an
 * UnavailableException or a TooManyRequestsException, neither of which is an IllegalArgumentException and so
 * neither caught by their inner handlers - silently killed scheduling until an operator restarted the
 * component. Nothing logged that it had happened beyond a single error line.
 *
 * Subclasses keep their own polling policy in {@link #pollOnce()}; this class owns only the thread, the
 * lifecycle and the never-die contract.
 */
public abstract class AbstractPollingScheduler implements Managed {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractPollingScheduler.class);

  private final String name;
  private final int pollingMinutes;
  private Thread thread;
  private volatile boolean running;

  protected AbstractPollingScheduler(String name, int pollingMinutes) {
    this.name = name;
    this.pollingMinutes = pollingMinutes;
  }

  /**
   * One polling cycle: check the preconditions, find work, submit it and sleep as this scheduler sees fit.
   * Expected to block, and to end promptly by throwing InterruptedException when its thread is interrupted.
   */
  protected abstract void pollOnce() throws InterruptedException;

  protected boolean isRunning() {
    return running;
  }

  protected int getPollingMinutes() {
    return pollingMinutes;
  }

  @Override
  public boolean hasStarted() {
    return running;
  }

  @Override
  public void start() throws Exception {
    if (pollingMinutes <= 0) {
      LOG.warn("{} is disabled", name);
      return;
    }
    if (running) {
      return;
    }
    running = true;
    thread = new Thread(this::poll, name);
    LOG.info("Start {}, polling every {} minutes", name, pollingMinutes);
    thread.start();
  }

  @Override
  public void stop() throws Exception {
    running = false;
    if (thread != null) {
      // the loop spends nearly all its time sleeping, so waiting for the poll interval to elapse
      // would make every shutdown as slow as the interval
      thread.interrupt();
      thread.join(ExecutorUtils.MILLIS_TO_DIE);
      thread = null;
    }
  }

  private void poll() {
    MDC.put(LoggingUtils.MDC_KEY_TASK, getClass().getSimpleName());
    try {
      while (running) {
        try {
          pollOnce();
        } catch (InterruptedException e) {
          LOG.info("Interrupted {}, stopping", name);
          Thread.currentThread().interrupt();
          return;
        } catch (RuntimeException e) {
          LOG.error("Error while scheduling in {}, backing off for {} minutes", name, pollingMinutes, e);
          if (!backOff()) {
            return;
          }
        }
      }
    } finally {
      running = false;
      MDC.remove(LoggingUtils.MDC_KEY_TASK);
    }
  }

  /** @return false if the sleep was interrupted and the scheduler should end */
  private boolean backOff() {
    try {
      TimeUnit.MINUTES.sleep(pollingMinutes);
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
