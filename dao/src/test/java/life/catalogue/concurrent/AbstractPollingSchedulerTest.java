package life.catalogue.concurrent;

import life.catalogue.api.exception.TooManyRequestsException;
import life.catalogue.api.exception.UnavailableException;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The contract these schedulers exist for: they are the only thread that ever submits their kind of work,
 * so a failed submission must back off, never end the loop.
 */
public class AbstractPollingSchedulerTest {

  static class TestScheduler extends AbstractPollingScheduler {
    final AtomicInteger polls = new AtomicInteger();
    final RuntimeException toThrow;

    TestScheduler(RuntimeException toThrow, int pollingMinutes) {
      super("test-scheduler", pollingMinutes);
      this.toThrow = toThrow;
    }

    @Override
    protected void pollOnce() throws InterruptedException {
      polls.incrementAndGet();
      if (toThrow != null) {
        throw toThrow;
      }
      TimeUnit.MILLISECONDS.sleep(5);
    }
  }

  private void awaitPolls(TestScheduler s, int min) throws InterruptedException {
    while (s.polls.get() < min) {
      TimeUnit.MILLISECONDS.sleep(2);
    }
  }

  /**
   * An unavailable job executor is exactly what a stopped JobExecutor component throws, and it is an
   * IllegalStateException - so it slipped past the old catch(IllegalArgumentException) into a
   * catch(Exception) that set running=false and killed continuous importing until someone restarted it.
   */
  @Test
  public void survivesUnavailableExecutor() throws Exception {
    var s = new TestScheduler(new UnavailableException("The JobExecutor is currently not available"), 1);
    s.start();
    awaitPolls(s, 1);
    TimeUnit.MILLISECONDS.sleep(50);
    assertTrue("the scheduler ended on an unavailable executor", s.hasStarted());
    s.stop();
    assertFalse(s.hasStarted());
  }

  /** A full queue is a RuntimeException but not an IllegalArgumentException either. */
  @Test
  public void survivesFullQueue() throws Exception {
    var s = new TestScheduler(new TooManyRequestsException("The IMPORT job queue is full"), 1);
    s.start();
    awaitPolls(s, 1);
    TimeUnit.MILLISECONDS.sleep(50);
    assertTrue("the scheduler ended on a full queue", s.hasStarted());
    s.stop();
  }

  @Test
  public void keepsPolling() throws Exception {
    var s = new TestScheduler(null, 1);
    s.start();
    awaitPolls(s, 3);
    assertTrue(s.hasStarted());
    s.stop();
    assertFalse(s.hasStarted());
  }

  /** Stopping must not wait out the polling interval, or every shutdown is as slow as the interval. */
  @Test
  public void stopIsPrompt() throws Exception {
    var s = new TestScheduler(new IllegalStateException("boom"), 1);
    s.start();
    awaitPolls(s, 1);
    long start = System.currentTimeMillis();
    s.stop();
    long took = System.currentTimeMillis() - start;
    assertTrue("stop took " + took + "ms while the scheduler slept off its back off", took < 5000);
  }

  @Test
  public void pollingOffMeansNoThread() throws Exception {
    var s = new TestScheduler(null, 0);
    s.start();
    assertFalse(s.hasStarted());
    assertEquals(0, s.polls.get());
    s.stop();
  }
}
