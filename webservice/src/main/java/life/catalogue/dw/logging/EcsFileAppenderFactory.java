package life.catalogue.dw.logging;

import java.util.Map;

import ch.qos.logback.core.OutputStreamAppender;

import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import co.elastic.logging.AdditionalField;
import co.elastic.logging.EcsJsonSerializer;
import co.elastic.logging.logback.EcsEncoder;

import com.fasterxml.jackson.annotation.JsonTypeName;

import io.dropwizard.logging.common.FileAppenderFactory;

import life.catalogue.api.util.ObjectUtils;

import life.catalogue.common.util.LoggingUtils;

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

import org.slf4j.MDC;

/**
 * A logback appender factory using the standardized ECS JSON logging from elasticsearch.
 * All MDC fields are included as root level properties.
 * The Dropwizard applicationName is added as the service.name ECS field.
 * Apart from that this logger can be configured to add the following fields
 *  - environment: any value e.g. prod, dev. Will be mapped to ECS service.environment
 *  - version: any value e.g. prod, dev. Will be mapped to ECS service.version
 *  - fields: an optional map of further fixed values to add to logs
 */
@JsonTypeName("ecs-file")
public class EcsFileAppenderFactory extends FileAppenderFactory<ILoggingEvent> {
  public static String VERSION;
  String version;
  String environment;
  boolean includeOrigin = false;
  boolean includeMarkers = false;
  boolean stackTraceAsArray = false;
  Map<String, String> fields;

  @JsonProperty
  public String getEnvironment() {
    return environment;
  }

  @JsonProperty
  public void setEnvironment(String environment) {
    this.environment = environment;
  }

  @JsonProperty
  public String getVersion() {
    return version;
  }

  @JsonProperty
  public void setVersion(String version) {
    this.version = version;
  }

  @JsonProperty
  public boolean isIncludeOrigin() {
    return includeOrigin;
  }

  @JsonProperty
  public void setIncludeOrigin(boolean includeOrigin) {
    this.includeOrigin = includeOrigin;
  }

  @JsonProperty
  public boolean isIncludeMarkers() {
    return includeMarkers;
  }

  @JsonProperty
  public void setIncludeMarkers(boolean includeMarkers) {
    this.includeMarkers = includeMarkers;
  }

  @JsonProperty
  public boolean isStackTraceAsArray() {
    return stackTraceAsArray;
  }

  @JsonProperty
  public void setStackTraceAsArray(boolean stackTraceAsArray) {
    this.stackTraceAsArray = stackTraceAsArray;
  }

  @JsonProperty
  public Map<String, String> getFields() {
    return fields;
  }

  @JsonProperty
  public void setFields(Map<String, String> fields) {
    this.fields = fields;
  }

  @Override
  public Appender<ILoggingEvent> build(LoggerContext context, String applicationName, LayoutFactory<ILoggingEvent> layoutFactory,
                           LevelFilterFactory<ILoggingEvent> levelFilterFactory, AsyncAppenderFactory<ILoggingEvent> asyncAppenderFactory) {
    OutputStreamAppender<ILoggingEvent> appender = appender(context);
    appender.setName("ecs-file-appender");

    var enc = new ClbEcsEncoder();
    enc.setContext(context);
    enc.setIncludeMarkers(includeMarkers);
    enc.setIncludeOrigin(includeOrigin);
    enc.setStackTraceAsArray(stackTraceAsArray);

    if (applicationName != null) {
      enc.setServiceName(applicationName);
    }
    if (environment != null) {
      enc.setServiceEnvironment(environment);
    }
    if (version != null || VERSION != null) {
      enc.setServiceVersion(ObjectUtils.coalesce(version, VERSION));
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