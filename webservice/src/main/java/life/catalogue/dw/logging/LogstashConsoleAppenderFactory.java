package life.catalogue.dw.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import com.fasterxml.jackson.annotation.JsonTypeName;
import net.logstash.logback.encoder.LogstashEncoder;

/**
 * A console appender that uses the same Logstash layout and MDC fields as the UDP logger
 */
@JsonTypeName("logstash-console")
public class LogstashConsoleAppenderFactory extends LogstashAppenderFactory<ILoggingEvent> {

  @Override
  Appender<ILoggingEvent> buildAppender(LoggerContext context, String customJson) {
    var appender = new ConsoleAppender<ILoggingEvent>();
    appender.setName("logstash-console-appender");
    LogstashEncoder enc = new LogstashEncoder();
    enc.setContext(context);
    enc.setCustomFields(customJson);
    enc.setIncludeMdc(true);
    enc.setIncludeContext(false);
    enc.setIncludeCallerData(false);
    enc.start();

    appender.setEncoder(enc);
    return appender;
  }
}