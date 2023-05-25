package life.catalogue.dw.logging;

import ch.qos.logback.classic.encoder.PatternLayoutEncoder;

import life.catalogue.common.util.LoggingUtils;

import java.io.File;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.sift.MDCBasedDiscriminator;
import ch.qos.logback.classic.sift.SiftingAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.sift.AppenderFactory;
import io.dropwizard.logging.AbstractAppenderFactory;
import io.dropwizard.logging.async.AsyncAppenderFactory;
import io.dropwizard.logging.filter.LevelFilterFactory;
import io.dropwizard.logging.layout.LayoutFactory;

import life.catalogue.concurrent.JobConfig;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A sifting file appender that writes logs separated by their MDC job key to individual files
 */
@JsonTypeName("job-appender")
public class JobAppenderFactory extends AbstractAppenderFactory<ILoggingEvent> {
  private static final Logger LOG = LoggerFactory.getLogger(JobAppenderFactory.class);

  private static final String PATTERN = "%d %X{task}: %msg%n";

  File directory;

  @JsonProperty
  public File getDirectory() {
    return directory;
  }

  public void setDirectory(File directory) {
    this.directory = directory;
  }

  @Override
  public Appender<ILoggingEvent> build(LoggerContext context, String applicationName, LayoutFactory<ILoggingEvent> layoutFactory, LevelFilterFactory<ILoggingEvent> levelFilterFactory, AsyncAppenderFactory<ILoggingEvent> asyncAppenderFactory) {
    var sift = new ClosingSiftingAppender();
    sift.setName("job-appender");
    sift.setContext(context);
    var filter = new MDCJobFilter();
    filter.start();
    sift.addFilter(filter);

    var discrimiator = new MDCBasedDiscriminator();
    discrimiator.setKey(LoggingUtils.MDC_KEY_JOB);
    discrimiator.setDefaultValue("none");
    discrimiator.start();
    sift.setDiscriminator(discrimiator);

    sift.setAppenderFactory(new AppenderFactory<ILoggingEvent>() {
      @Override
      public Appender<ILoggingEvent> buildAppender(Context context, String discriminatingValue) throws JoranException {
        var fa = new GZipFileAppender<ILoggingEvent>();
        var dir = JobConfig.jobLog(directory, discriminatingValue).getAbsolutePath();
        LOG.debug("Starting new job log appender at {}", dir);
        fa.setFile(dir);
        fa.setContext(context);
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setPattern(PATTERN);
        encoder.setContext(context);
        encoder.start();
        fa.setEncoder(encoder);
        fa.start();
        return fa;
      }
    });

    sift.addFilter(levelFilterFactory.build(threshold));
    getFilterFactories().forEach(f -> sift.addFilter(f.build()));
    sift.start();

    if (!directory.exists() && !directory.mkdirs()) {
      LOG.error("Failed to create missing job log directory {}", directory);
    }

    LOG.info("Created asynchroneous job appender for directory {}", directory);
    return wrapAsync(sift, asyncAppenderFactory);
  }
}