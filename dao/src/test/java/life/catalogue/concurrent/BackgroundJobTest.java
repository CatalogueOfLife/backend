package life.catalogue.concurrent;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.common.util.LoggingUtils;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Test;
import org.slf4j.MDC;

import static org.junit.Assert.*;

public class BackgroundJobTest {

  /**
   * Records the job key it sees in the MDC while it runs.
   */
  static class Inner extends BackgroundJob {
    String jobMdc;

    Inner(boolean logToCallerJob) {
      super(1);
      this.logToCallerJob = logToCallerJob;
    }

    @Override
    public void execute() {
      jobMdc = MDC.get(LoggingUtils.MDC_KEY_JOB);
    }
  }

  /**
   * Runs an inner job on its own thread, the way the XRelease merges its sectors.
   */
  static class Outer extends BackgroundJob {
    final Inner inner;
    Map<String, String> before;
    Map<String, String> after;

    Outer(Inner inner) {
      super(1);
      this.inner = inner;
    }

    @Override
    public void execute() {
      LoggingUtils.setDatasetMDC(3, 632, getClass());
      before = MDC.getCopyOfContextMap();
      inner.run();
      after = MDC.getCopyOfContextMap();
    }
  }

  @After
  public void clear() {
    MDC.clear();
  }

  @Test
  public void nestedRunRestoresCallerMdc() {
    var outer = new Outer(new Inner(false));
    outer.run();

    assertEquals(JobStatus.FINISHED, outer.getStatus());
    assertEquals(JobStatus.FINISHED, outer.inner.getStatus());
    // the inner job logs under its own key while it runs
    assertEquals(outer.inner.getKey().toString(), outer.inner.jobMdc);
    // and hands the caller its MDC back untouched
    assertEquals(outer.getKey().toString(), outer.before.get(LoggingUtils.MDC_KEY_JOB));
    assertEquals(outer.before, outer.after);
    // a job run by an executor thread leaves nothing behind
    assertMdcEmpty();
  }

  @Test
  public void embeddedRunLogsUnderCallerJob() {
    var outer = new Outer(new Inner(true));
    outer.run();

    assertEquals(JobStatus.FINISHED, outer.inner.getStatus());
    assertEquals(outer.getKey().toString(), outer.inner.jobMdc);
    assertEquals(outer.before, outer.after);
  }

  @Test
  public void embeddedRunWithoutCallerUsesOwnKey() {
    var inner = new Inner(true);
    inner.run();
    assertEquals(inner.getKey().toString(), inner.jobMdc);
    assertMdcEmpty();
  }

  /**
   * A request error deep in the cause chain is the reason, like the IllegalArgumentException
   * a MyBatis result mapping wraps together with its whole SQL.
   */
  @Test
  public void errorReason() {
    var job = new Inner(false);
    assertNull(job.getErrorReason());

    job.setError(new RuntimeException("### Error querying database. ### SQL: SELECT ...",
      new IllegalStateException("Could not set property 'status'",
        new IllegalArgumentException("Taxa cannot have a SYNONYM status"))));
    assertEquals("Taxa cannot have a SYNONYM status", job.getErrorReason());

    job.setError(new NotFoundException("Root taxon x does not exist"));
    assertEquals("Root taxon x does not exist", job.getErrorReason());

    // internal errors have no reason meant for the user
    job.setError(new NullPointerException("npe"));
    assertNull(job.getErrorReason());
  }

  private static void assertMdcEmpty() {
    var mdc = MDC.getCopyOfContextMap();
    assertTrue("MDC left behind: " + mdc, mdc == null || mdc.isEmpty());
  }
}
