package life.catalogue.dw.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;

import com.fasterxml.jackson.annotation.JsonTypeName;

import io.dropwizard.logging.common.filter.FilterFactory;

import life.catalogue.cache.VarnishUtils;
import life.catalogue.event.EventBroker;

@JsonTypeName("events-only")
public class EventFilterFactory implements FilterFactory<ILoggingEvent> {
  @Override
  public Filter<ILoggingEvent> build() {
    return new PackageFilter(EventBroker.class.getPackageName(), VarnishUtils.class.getName());
  }
}
