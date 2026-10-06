package life.catalogue.dw.logging;

import life.catalogue.api.model.JobResult;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobConfig;
import life.catalogue.config.ReleaseConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;

import static org.junit.Assert.*;

public class JobAppenderTest {
  private static final Logger LOG = LoggerFactory.getLogger(JobAppenderTest.class);
  private static final int DATASET = 3;
  private static final int ATTEMPT = 632;

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private ch.qos.logback.classic.Logger target;
  private JobAppender appender;

  /**
   * Stands in for a sector sync the XRelease merges on its own thread.
   */
  static class Embedded extends BackgroundJob {
    Embedded() {
      super(1);
      logToCallerJob = true;
    }

    @Override
    public void execute() {
      LOG.info("merging a sector");
    }
  }

  /**
   * Stands in for an XRelease: logs to its own file and copies it to the release reports, just like ProjectRelease.
   */
  static class Release extends BackgroundJob {
    Release() {
      super(1);
      logToFile = true;
    }

    @Override
    public void execute() {
      LoggingUtils.setDatasetMDC(DATASET, ATTEMPT, getClass());
      LOG.info("before the merge");
      new Embedded().run();
      LOG.info("after the merge");
    }

    @Override
    protected void onLogAppenderClose() {
      LoggingUtils.setDatasetMDC(DATASET, ATTEMPT, getClass());
      LOG.info(LoggingUtils.COPY_RELEASE_LOGS_MARKER, "Copy release logs");
    }
  }

  @Before
  public void init() throws IOException {
    var ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
    appender = new JobAppender(tmp.newFolder("jobs"), tmp.newFolder("downloads"), tmp.newFolder("releases"), "%msg%n");
    appender.setContext(ctx);
    var filter = new MDCJobFilter();
    filter.start();
    appender.addFilter(filter);
    appender.start();
    target = ctx.getLogger("life.catalogue");
    target.addAppender(appender);
  }

  @After
  public void detach() {
    target.detachAppender(appender);
    appender.stop();
  }

  @Test
  public void embeddedJobKeepsReleaseLog() throws IOException {
    var job = new Release();
    job.run();

    assertTrue("job log appender leaked", appender.appender.isEmpty());

    File download = new File(appender.downloadDir, JobResult.downloadLogFilePath(job.getKey()));
    File report = new File(ReleaseConfig.reportDir(appender.reportDir, DATASET, ATTEMPT), "job.log.gz");
    for (File f : new File[]{download, report}) {
      assertTrue(f + " missing", f.exists());
      String log = gunzip(f);
      assertTrue(log, log.contains("before the merge"));
      assertTrue(log, log.contains("merging a sector"));
      assertTrue(log, log.contains("after the merge"));
    }
    // the live log stays where JobCleanup expects it
    assertTrue(JobConfig.jobLog(appender.directory, job.getKey().toString()).exists());
  }

  private static String gunzip(File f) throws IOException {
    try (var in = new GZIPInputStream(new FileInputStream(f))) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
