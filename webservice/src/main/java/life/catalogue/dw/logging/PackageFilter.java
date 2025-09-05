package life.catalogue.dw.logging;

import life.catalogue.common.util.LoggingUtils;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Filter that makes sure the MDC dataset property is set.
 */
public class PackageFilter extends Filter<ILoggingEvent> {
  private final String packageName;

  public PackageFilter(String packageName) {
    this.packageName = packageName;
  }

  @Override
  public FilterReply decide(ILoggingEvent event) {
    if (event.getLoggerName().startsWith(packageName)) {
      return FilterReply.ACCEPT;
    }
    return FilterReply.DENY;
  }
}
