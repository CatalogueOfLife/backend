package life.catalogue.dw.logging;

import java.util.Map;

import ch.qos.logback.core.OutputStreamAppender;

import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import co.elastic.logging.AdditionalField;
import co.elastic.logging.logback.EcsEncoder;

import com.fasterxml.jackson.annotation.JsonTypeName;

import io.dropwizard.logging.common.FileAppenderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import io.dropwizard.logging.common.AbstractAppenderFactory;
import io.dropwizard.logging.common.async.AsyncAppenderFactory;
import io.dropwizard.logging.common.filter.LevelFilterFactory;
import io.dropwizard.logging.common.layout.LayoutFactory;

/**
 * A logback appender factory using the standardized ECS JSON logging from elasticsearch.
 * All MDC fields are included as root level properties.
 * The Dropwizard applicationName is added as the application field.
 * Apart from that this logger can be configured to add the following fields to the JSON:
 *  - environment: any value e.g. prod, dev
 *  - fields: an optional map of further fixed values to add to logs
 */
@JsonTypeName("ecs-file")
public class EcsFileAppenderFactory extends FileAppenderFactory<ILoggingEvent> {
  String environment;
  Map<String, String> fields;

  @JsonProperty
  public String getEnvironment() {
    return environment;
  }

  @JsonProperty
  public void setEnvironment(String environment) {
    this.environment = environment;
  }

  public Map<String, String> getFields() {
    return fields;
  }

  public void setFields(Map<String, String> fields) {
    this.fields = fields;
  }

  @Override
  public Appender<ILoggingEvent> build(LoggerContext context, String applicationName, LayoutFactory<ILoggingEvent> layoutFactory,
                           LevelFilterFactory<ILoggingEvent> levelFilterFactory, AsyncAppenderFactory<ILoggingEvent> asyncAppenderFactory) {
    OutputStreamAppender<ILoggingEvent> appender = appender(context);
    appender.setName("ecs-file-appender");

    var enc = new EcsEncoder();
    enc.setContext(context);
    if (applicationName != null) {
      enc.addAdditionalField(new AdditionalField("application", applicationName));
      enc.setServiceName(applicationName);
    }
    if (environment != null) {
      enc.addAdditionalField(new AdditionalField("environment", environment));
      enc.setServiceEnvironment(environment);
    }
    if (fields != null) {
      for (var f : fields.entrySet()) {
        enc.addAdditionalField(new AdditionalField(f.getKey(), f.getValue()));
      }
    }
    enc.start();
    appender.setEncoder(enc);

    appender.addFilter(levelFilterFactory.build(threshold));
    this.getFilterFactories().forEach((f) -> {
      appender.addFilter(f.build());
    });
    appender.start();
    return wrapAsync(appender, asyncAppenderFactory);
  }

}