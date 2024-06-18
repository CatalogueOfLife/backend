package life.catalogue.dw.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggerContextVO;
import co.elastic.logging.EcsJsonSerializer;
import co.elastic.logging.JsonUtils;
import co.elastic.logging.logback.EcsEncoder;

import life.catalogue.common.util.LoggingUtils;

import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClbEcsEncoder extends EcsEncoder {
  private static final Map<String, String> ECS_MAP = Map.of(
    LoggingUtils.MDC_KEY_JOB, "process.pid",
    LoggingUtils.MDC_KEY_TASK, "process.name"
  );

  @Override
  public byte[] encode(ILoggingEvent event) {
    return super.encode(new WrappedEvent(event));
  }

  @Override
  protected void addCustomFields(ILoggingEvent event, StringBuilder builder) {
    if (event instanceof WrappedEvent) {
      var we = (WrappedEvent) event;
      for (var kvp : we.getECSPropertyMap().entrySet()) {
        builder.append('"');
        JsonUtils.quoteAsString(kvp.getKey(), builder);
        builder.append("\":\"");
        JsonUtils.quoteAsString(EcsJsonSerializer.toNullSafeString(kvp.getValue()), builder);
        builder.append("\",");
      }
    }
  }

  private static class WrappedEvent implements ILoggingEvent {
    private final ILoggingEvent event;
    private final Map<String, String>  mdc = new HashMap<>();
    private final Map<String, String>  ecs = new HashMap<>();

    WrappedEvent(ILoggingEvent event) {
      this.event = event;
      // remove ECS relevant entries to avoid duplicates
      var props = event.getMDCPropertyMap();
      if (props != null && !props.isEmpty()) {
        for (var kvp : props.entrySet()) {
          if (!ECS_MAP.containsKey(kvp.getKey())) {
            mdc.put(kvp.getKey(), kvp.getValue());
          } else {
            ecs.put(ECS_MAP.get(kvp.getKey()), kvp.getValue());
          }
        }
      }
    }

    public Map<String, String> getECSPropertyMap() {
      return ecs;
    }

    @Override
    public String getThreadName() {
      return event.getThreadName();
    }

    @Override
    public Level getLevel() {
      return event.getLevel();
    }

    @Override
    public String getMessage() {
      return event.getMessage();
    }

    @Override
    public Object[] getArgumentArray() {
      return event.getArgumentArray();
    }

    @Override
    public String getFormattedMessage() {
      return event.getFormattedMessage();
    }

    @Override
    public String getLoggerName() {
      return event.getLoggerName();
    }

    @Override
    public LoggerContextVO getLoggerContextVO() {
      return event.getLoggerContextVO();
    }

    @Override
    public IThrowableProxy getThrowableProxy() {
      return event.getThrowableProxy();
    }

    @Override
    public StackTraceElement[] getCallerData() {
      return event.getCallerData();
    }

    @Override
    public boolean hasCallerData() {
      return event.hasCallerData();
    }

    @Override
    public Marker getMarker() {
      return event.getMarker();
    }

    @Override
    public List<Marker> getMarkerList() {
      return event.getMarkerList();
    }

    @Override
    public Map<String, String> getMDCPropertyMap() {
      return mdc;
    }

    @Override
    public Map<String, String> getMdc() {
      return mdc;
    }

    @Override
    public long getTimeStamp() {
      return event.getTimeStamp();
    }

    @Override
    public int getNanoseconds() {
      return event.getNanoseconds();
    }

    @Override
    public Instant getInstant() {
      return event.getInstant();
    }

    @Override
    public long getSequenceNumber() {
      return event.getSequenceNumber();
    }

    @Override
    public List<KeyValuePair> getKeyValuePairs() {
      return event.getKeyValuePairs();
    }

    @Override
    public void prepareForDeferredProcessing() {
      event.prepareForDeferredProcessing();
    }
  }
}
