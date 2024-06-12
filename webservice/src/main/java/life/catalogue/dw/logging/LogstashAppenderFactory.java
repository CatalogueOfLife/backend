package life.catalogue.dw.logging;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.spi.DeferredProcessingAware;
import io.dropwizard.logging.common.AbstractAppenderFactory;
import io.dropwizard.logging.common.async.AsyncAppenderFactory;
import io.dropwizard.logging.common.filter.LevelFilterFactory;
import io.dropwizard.logging.common.layout.LayoutFactory;

/**
 * An abstract Logstash appender factory using MDC fields to provide additional configurable logstash fields for regular logging.
 * Apart from MDC fiels this logger adds the following fields to the logstash JSON:
 *  - environment: any value e.g. prod, dev
 *  - application: fixed application name, e.g. ws-server
 *  - extra: an optional map of further fixed values to add to logs
 */
abstract class LogstashAppenderFactory<E extends DeferredProcessingAware> extends AbstractAppenderFactory<E> {
  private static final Logger LOG = LoggerFactory.getLogger(LogstashAppenderFactory.class);

  String environment;
  Map<String, String> extra;

  @JsonProperty
  public String getEnvironment() {
    return environment;
  }
  
  @JsonProperty
  public void setEnvironment(String environment) {
    this.environment = environment;
  }
  
  public Map<String, String> getExtra() {
    return extra;
  }

  public void setExtra(Map<String, String> extra) {
    this.extra = extra;
  }

  @Override
  public Appender<E> build(LoggerContext context, String applicationName, LayoutFactory<E> layoutFactory,
                           LevelFilterFactory<E> levelFilterFactory, AsyncAppenderFactory<E> asyncAppenderFactory) {
    final String customJson = customFieldJson(applicationName);
    Appender<E> appender = buildAppender(context, customJson);
    appender.setContext(context);
    appender.addFilter(levelFilterFactory.build(threshold));
    getFilterFactories().forEach(f -> appender.addFilter(f.build()));
    appender.start();

    LOG.debug("Created asynchroneous (queue={}, custom={}) {} appender for env {}", getQueueSize(), customJson, appender.getClass().getSimpleName(), environment);
    return wrapAsync(appender, asyncAppenderFactory);
  }

  abstract Appender<E> buildAppender(LoggerContext context, String customJson);

  private String customFieldJson(String applicationName) {
    StringBuilder sb = new StringBuilder();
    sb.append("{")
      .append("\"environment\":\"col");
    if (environment != null) {
      sb.append("-")
        .append(Strings.nullToEmpty(environment));
    }
    sb.append("\",")
      .append("\"application\":\"")
      .append(applicationName)
      .append("\"");
    if (extra != null) {
      extra.forEach((key, value) -> {
        sb.append(",\"")
            .append(key)
            .append("\":\"")
            .append(value)
            .append("\"");
      });
    }
    sb.append("}");
    return sb.toString();
  }
}