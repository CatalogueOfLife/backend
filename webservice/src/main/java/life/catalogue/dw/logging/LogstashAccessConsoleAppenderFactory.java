package life.catalogue.dw.logging;

import ch.qos.logback.access.spi.IAccessEvent;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import com.fasterxml.jackson.annotation.JsonTypeName;
import net.logstash.logback.encoder.LogstashAccessEncoder;

/**
 * A console appender that logs the same MDC fields as the UDP logger,
 * but alos logs the basic http access fields using a LogstashAccessEncoder extended with the following fields:
 *
 *  - User-Agent header
 */
@JsonTypeName("logstash-access-console")
public class LogstashAccessConsoleAppenderFactory extends LogstashAppenderFactory<IAccessEvent> {

  @Override
  Appender<IAccessEvent> buildAppender(LoggerContext context, String customJson) {
    var appender = new ConsoleAppender<IAccessEvent>();
    appender.setName("logstash-access-console-appender");

    LogstashAccessEncoder enc = new LogstashAccessEncoder();
    enc.setContext(context);
    enc.setCustomFields(customJson);
    enc.setIncludeContext(false);
    // expose User-Agent header
    enc.addProvider(new UserAgentJsonProvider());
    enc.addProvider(new LoggerNameJsonProvider());
    enc.start();

    appender.setEncoder(enc);
    return appender;
  }
}