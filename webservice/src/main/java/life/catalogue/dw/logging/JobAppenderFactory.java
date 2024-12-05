package life.catalogue.dw.logging;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import io.dropwizard.logging.common.AbstractAppenderFactory;
import io.dropwizard.logging.common.async.AsyncAppenderFactory;
import io.dropwizard.logging.common.filter.LevelFilterFactory;
import io.dropwizard.logging.common.layout.LayoutFactory;

/**
 * A file appender that writes logs separated by their MDC job key to individual files
 */
@JsonTypeName("job-appender")
public class JobAppenderFactory extends AbstractAppenderFactory<ILoggingEvent> {
  private static final Logger LOG = LoggerFactory.getLogger(JobAppenderFactory.class);


  private String pattern;
  private File directory;
  private File downloadDir;
  private File reportDir;

  @JsonProperty
  public String getPattern() {
    return pattern;
  }

  public void setPattern(String pattern) {
    this.pattern = pattern;
  }

  @JsonProperty
  public File getDirectory() {
    return directory;
  }

  public void setDirectory(File directory) {
    this.directory = directory;
  }

  @JsonProperty
  public File getDownloadDir() {
    return downloadDir;
  }

  public void setDownloadDir(File downloadDir) {
    this.downloadDir = downloadDir;
  }

  @JsonProperty
  public File getReportDir() {
    return reportDir;
  }

  public void setReportDir(File reportDir) {
    this.reportDir = reportDir;
  }

  @Override
  public Appender<ILoggingEvent> build(LoggerContext context, String applicationName, LayoutFactory<ILoggingEvent> layoutFactory, LevelFilterFactory<ILoggingEvent> levelFilterFactory, AsyncAppenderFactory<ILoggingEvent> asyncAppenderFactory) {
    var app = new JobAppender(directory, downloadDir, reportDir, pattern);
    app.setName("job-appender");
    app.setContext(context);

    var filter = new MDCJobFilter();
    filter.start();
    app.addFilter(filter);

    app.addFilter(levelFilterFactory.build(threshold));
    getFilterFactories().forEach(f -> app.addFilter(f.build()));
    app.start();

    LOG.info("Created job appender for directory {}", directory);
    return app;
  }

}