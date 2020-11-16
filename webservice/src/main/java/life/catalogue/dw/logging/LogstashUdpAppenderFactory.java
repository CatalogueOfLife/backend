package life.catalogue.dw.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import net.logstash.logback.appender.LogstashUdpSocketAppender;
import net.logstash.logback.layout.LogstashLayout;

/**
 *  Configure the logstash UDP appender factory for logstash with
 *  - host: logstash host
 *  - port: logstash port
 */
@JsonTypeName("logstash")
public class LogstashUdpAppenderFactory extends LogstashAppenderFactory<ILoggingEvent> {

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
  Appender<ILoggingEvent> buildAppender(LoggerContext context, String customJson) {
    LogstashUdpSocketAppender appender = new LogstashUdpSocketAppender();
    appender.setName("logstash-console-appender");
    appender.setHost(host);
    appender.setPort(port);

    LogstashLayout layout = new LogstashLayout();
    layout.setTimeZone("UTC");
    layout.setCustomFields(customJson);
    layout.setIncludeMdc(true);
    layout.setIncludeContext(false);
    layout.setIncludeCallerData(false);

    appender.setLayout(layout);
    return appender;
  }
}