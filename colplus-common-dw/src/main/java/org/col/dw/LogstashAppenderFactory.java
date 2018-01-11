package org.col.dw;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.dropwizard.logging.AbstractAppenderFactory;
import io.dropwizard.logging.async.AsyncAppenderFactory;
import io.dropwizard.logging.filter.LevelFilterFactory;
import io.dropwizard.logging.layout.LayoutFactory;
import net.logstash.logback.appender.LogstashSocketAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Use MDC fields to provide additional logstash fields
 */
@JsonTypeName("logstash")
public class LogstashAppenderFactory extends AbstractAppenderFactory<ILoggingEvent> {
  private static final Logger LOG = LoggerFactory.getLogger(LogstashAppenderFactory.class);

  private int port;
  private String host;

  @JsonProperty
  public int getPort() {
    return port;
  }

  @JsonProperty
  public void setPort(int port) {
    this.port = port;
  }

  @JsonProperty
  public String getHost() {
    return host;
  }

  @JsonProperty
  public void setHost(String host) {
    this.host = host;
  }

  @Override
  public Appender<ILoggingEvent> build(LoggerContext context, String applicationName, LayoutFactory<ILoggingEvent> layoutFactory,
                           LevelFilterFactory<ILoggingEvent> levelFilterFactory, AsyncAppenderFactory<ILoggingEvent> asyncAppenderFactory) {

    final LogstashSocketAppender appender = new LogstashSocketAppender();
    appender.setName("logstash-appender");
    appender.setContext(context);
    appender.setHost(host);
    appender.setPort(port);
    appender.setIncludeCallerData(isIncludeCallerData());
    appender.setCustomFields(customFieldJson(applicationName));
    appender.addFilter(levelFilterFactory.build(threshold));
    getFilterFactories().forEach(f -> appender.addFilter(f.build()));
    appender.start();

    LOG.debug("Created asynchroneous (queue={}, custom={}) logstash appender for {}:{}", getQueueSize(), appender.getCustomFields(), host, port);
    return wrapAsync(appender, asyncAppenderFactory);
  }

  public static String customFieldJson(String applicationName){
    StringBuilder sb = new StringBuilder();
    sb.append("{")
        .append("\"environment\":\"col\",")
        .append("\"class\":\"")
        .append(applicationName)
        .append("\"")
        .append("}");
    return sb.toString();
  }
}