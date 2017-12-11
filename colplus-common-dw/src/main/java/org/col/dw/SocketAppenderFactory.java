package org.col.dw;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.net.SocketAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.util.Duration;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.dropwizard.logging.AbstractAppenderFactory;
import io.dropwizard.logging.async.AsyncAppenderFactory;
import io.dropwizard.logging.filter.LevelFilterFactory;
import io.dropwizard.logging.layout.LayoutFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
@JsonTypeName("socket")
public class SocketAppenderFactory extends AbstractAppenderFactory<ILoggingEvent> {
  private static final Logger LOG = LoggerFactory.getLogger(SocketAppenderFactory.class);

  private int port;
  private String host;
  private int reconnectionDelay = 10000;

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

  @JsonProperty
  public int getReconnectionDelay() {
    return reconnectionDelay;
  }

  @JsonProperty
  public void setReconnectionDelay(int reconnectionDelay) {
    this.reconnectionDelay = reconnectionDelay;
  }

  @Override
  public Appender<ILoggingEvent> build(LoggerContext context, String applicationName, LayoutFactory<ILoggingEvent> layoutFactory,
                           LevelFilterFactory<ILoggingEvent> levelFilterFactory, AsyncAppenderFactory<ILoggingEvent> asyncAppenderFactory) {

    final SocketAppender appender = new SocketAppender();
    appender.setName("socket-appender");
    appender.setContext(context);
    appender.setRemoteHost(host);
    appender.setPort(port);
    appender.setIncludeCallerData(isIncludeCallerData());
    appender.setQueueSize(getQueueSize());
    appender.setReconnectionDelay(new Duration(reconnectionDelay));

    appender.addFilter(levelFilterFactory.build(threshold));
    getFilterFactories().forEach(f -> appender.addFilter(f.build()));
    appender.start();

    LOG.info("Created socket appender for {}:{}", host, port);
    return wrapAsync(appender, asyncAppenderFactory);
  }
}