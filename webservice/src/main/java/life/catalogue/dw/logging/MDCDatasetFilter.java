package life.catalogue.dw.logging;

import life.catalogue.common.util.LoggingUtils;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Filter that makes sure the MDC dataset property is set.
 */
public class MDCDatasetFilter extends Filter<ILoggingEvent> {

  @Override
  public FilterReply decide(ILoggingEvent event) {
    if (event.getMDCPropertyMap().containsKey(LoggingUtils.MDC_KEY_DATASET)) {
      return FilterReply.ACCEPT;
    }
    return FilterReply.DENY;
  }
}
