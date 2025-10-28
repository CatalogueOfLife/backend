package life.catalogue.dw.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Filter that makes sure the logger is in one of the given package names
 */
public class PackageFilter extends Filter<ILoggingEvent> {
  private final String[] packageNames;

  public PackageFilter(String... packageNames) {
    this.packageNames = packageNames;
  }

  @Override
  public FilterReply decide(ILoggingEvent event) {
    for (String packageName : packageNames) {
      if (event.getLoggerName().startsWith(packageName)) {
        return FilterReply.ACCEPT;
      }
    }
    return FilterReply.DENY;
  }
}
