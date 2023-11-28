package life.catalogue.dw.logging;

import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;

import com.fasterxml.jackson.annotation.JsonProperty;

import life.catalogue.api.model.JobResult;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.concurrent.JobConfig;
import life.catalogue.config.ReleaseConfig;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import javax.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class JobAppender extends AppenderBase<ILoggingEvent> {
  private static final Logger LOG = LoggerFactory.getLogger(JobAppender.class);
  final Map<String, Appender<ILoggingEvent>> appender = new ConcurrentHashMap<>();

  String pattern = "%d %-5level %-25logger{0} %6X{source} %msg%n";
  File directory;
  File downloadDir;
  File reportDir;

  public JobAppender() {
  }

  public JobAppender(File directory, File downloadDir, File reportDir, @Nullable String pattern) {
    this.directory = directory;
    this.downloadDir = downloadDir;
    this.reportDir = reportDir;
    if (pattern != null) {
      this.pattern = pattern;
    }
  }

  @JsonProperty
  public String getDirectory() {
    return str(directory);
  }

  public void setDirectory(String directory) {
    this.directory = parseFile(directory);
  }

  @JsonProperty
  public String getDownloadDir() {
    return str(downloadDir);
  }

  public void setDownloadDir(String downloadDir) {
    this.downloadDir = parseFile(downloadDir);
  }

  @JsonProperty
  public String getReportDir() {
    return str(reportDir);
  }

  public void setReportDir(String reportDir) {
    this.reportDir = parseFile(reportDir);
  }

  private static File parseFile(String file) {
    if (!StringUtils.isBlank(file)) {
      // Trim spaces from both ends. The users probably does not want
      // trailing spaces in file names.
      return new File(file.trim());
    }
    return null;
  }

  private static String str(File file) {
    if (file != null) {
      return file.getAbsolutePath();
    }
    return null;
  }

  @Override
  public void start() {
    if (!directory.exists() && !directory.mkdirs()) {
      LOG.error("Failed to create missing job log directory {}", directory);
    }
    if (downloadDir != null && !downloadDir.exists() && !downloadDir.mkdirs()) {
      LOG.error("Failed to create missing download directory {}", downloadDir);
    }
    if (reportDir != null && !reportDir.exists() && !reportDir.mkdirs()) {
      LOG.error("Failed to create missing report directory {}", reportDir);
    }
    super.start();
  }

  @Override
  public void stop() {
    super.stop();
    for (var a : appender.values()) {
      a.stop();
    }
  }

  @Override
  protected void append(ILoggingEvent log) {
    final String key = log.getMDCPropertyMap().get(LoggingUtils.MDC_KEY_JOB);
    Appender<ILoggingEvent> a;
    if (hasMarker(log, LoggingUtils.END_JOB_LOG_MARKER)) {
      a = appender.remove(key);
      if (a != null) {
        a.doAppend(log);
        a.stop();
        // copy job logs to download directory
        if (downloadDir != null) {
          copyToDownloads(log, key);
        }
      }
    } else {
      a = appender.computeIfAbsent(key, this::newAppender);
      a.doAppend(log);
    }

    // copy job logs also to release dir?
    if (reportDir != null && hasMarker(log, LoggingUtils.COPY_RELEASE_LOGS_MARKER)) {
      copyToReports(log, key);
    }
  }

  private void copyToDownloads(ILoggingEvent log, String key) {
    File logFile = JobConfig.jobLog(directory, key);
    if (logFile.exists()) {
      LOG.info("Copy logs for job {} from {}", key, logFile);
      try {
        var uuid = UUID.fromString(key.trim());
        var df = new File(downloadDir, JobResult.downloadFilePath(uuid, "log.gz"));
        FileUtils.copyFile(logFile, df);
      } catch (IllegalArgumentException e) {
        LOG.error("Not a job key. Cannot copy logs for {}", key, e);
      } catch (IOException e) {
        LOG.error("Failed to copy job logs from {}", log, e);
      }
    } else {
      LOG.warn("Logs for job {} missing", key);
    }
  }

  private void copyToReports(ILoggingEvent log, String key) {
    try {
      int datasetKey = Integer.parseInt(log.getMDCPropertyMap().get(LoggingUtils.MDC_KEY_DATASET));
      int attempt = Integer.parseInt(log.getMDCPropertyMap().get(LoggingUtils.MDC_KEY_ATTEMPT));
      File rdir = ReleaseConfig.reportDir(reportDir, datasetKey, attempt);
      var target = new File(rdir, "job.log.gz");
      File logFile = JobConfig.jobLog(directory, key);
      FileUtils.copyFile(logFile, target);
    } catch (IllegalArgumentException e) {
      LOG.error("Bad dataset ({}) or attempt ({}) key. Cannot copy logs to release reports",
        log.getMDCPropertyMap().get(LoggingUtils.MDC_KEY_DATASET),
        log.getMDCPropertyMap().get(LoggingUtils.MDC_KEY_ATTEMPT)
      );
    } catch (IOException e) {
      LOG.error("Failed to copy job logs for {}", key, e);
    }
  }

  private Appender<ILoggingEvent> newAppender(String key) {
    final var fa = new GZipFileAppender<ILoggingEvent>();
    var jobFile = JobConfig.jobLog(directory, key).getAbsolutePath();
    LOG.debug("Starting new job log appender at {}", jobFile);
    fa.setFile(jobFile);
    fa.setContext(context);
    PatternLayoutEncoder encoder = new PatternLayoutEncoder();
    encoder.setPattern(pattern);
    encoder.setContext(context);
    encoder.start();
    fa.setEncoder(encoder);
    fa.start();
    return fa;
  }

  private boolean hasMarker(ILoggingEvent event, Marker marker) {
    Marker m = event.getMarker();
    if (m == null)
      return false;

    return m.contains(marker.getName());
  }
}
