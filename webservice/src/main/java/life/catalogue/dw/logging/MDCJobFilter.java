package life.catalogue.dw.logging;

import life.catalogue.common.util.LoggingUtils;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Filter that makes sure the MDC job UUID property is set.
 */
public class MDCJobFilter extends Filter<ILoggingEvent> {

  @Override
  public FilterReply decide(ILoggingEvent event) {
    if (event.getMDCPropertyMap().containsKey(LoggingUtils.MDC_KEY_JOB)) {
      return FilterReply.ACCEPT;
    }
    return FilterReply.DENY;
  }
}
