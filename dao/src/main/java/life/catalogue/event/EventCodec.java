package life.catalogue.event;

import life.catalogue.api.event.Event;
import life.catalogue.api.event.FlushCaches;

import java.nio.charset.StandardCharsets;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Turns events into the JSON payload of a postgres notification and back.
 * Unknown properties and event types are ignored, so two app versions can read each other's events.
 */
public class EventCodec {
  private static final Logger LOG = LoggerFactory.getLogger(EventCodec.class);
  /**
   * Postgres rejects notification payloads of 8000 bytes or more.
   */
  static final int MAX_BYTES = 7900;
  private final ObjectMapper mapper = new ObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false);

  /**
   * @return the JSON of the event, of its smaller version if it is too big to be sent, or of a FlushCaches event
   *         if that is too big still
   */
  public String encode(Event event) {
    try {
      String json = mapper.writeValueAsString(event);
      if (tooBig(json)) {
        Event smaller = event.smaller();
        if (smaller != null) {
          LOG.info("Event {} is too big to be broadcast, send a smaller version", event);
          json = mapper.writeValueAsString(smaller);
        }
        if (smaller == null || tooBig(json)) {
          LOG.warn("Event {} is too big to be broadcast, ask every app to flush its caches instead", event);
          json = mapper.writeValueAsString(new FlushCaches());
        }
      }
      return json;
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Cannot encode event " + event, e);
    }
  }

  private static boolean tooBig(String json) {
    return json.getBytes(StandardCharsets.UTF_8).length >= MAX_BYTES;
  }

  /**
   * @return the event or null if it cannot be read, e.g. a type this version does not know
   */
  @Nullable
  public Event decode(String json) {
    try {
      return mapper.readValue(json, Event.class);
    } catch (JsonProcessingException | RuntimeException e) {
      LOG.warn("Ignore unreadable event {}: {}", json, e.getMessage());
      return null;
    }
  }
}
