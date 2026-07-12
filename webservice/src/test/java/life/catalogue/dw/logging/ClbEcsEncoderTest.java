package life.catalogue.dw.logging;

import life.catalogue.common.util.LoggingUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.Test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClbEcsEncoderTest {

  private static String encode(Map<String, String> mdc) {
    var enc = new ClbEcsEncoder();
    enc.start();
    var event = new LoggingEvent();
    event.setLoggerName("life.catalogue.assembly.SectorRunnable");
    event.setThreadName("assembly-sync-1");
    event.setLevel(Level.INFO);
    event.setMessage("Completed SectorSync for sector 310368:2");
    event.setTimeStamp(System.currentTimeMillis());
    event.setMDCPropertyMap(mdc);
    return new String(enc.encode(event), StandardCharsets.UTF_8);
  }

  /**
   * The "source" MDC key collides with the reserved ECS "source" object field. Writing it as a top level scalar makes
   * Elasticsearch reject the whole document, so the log line never reaches Kibana. It must be remapped to a non
   * colliding field instead, while unrelated MDC keys keep being written verbatim.
   */
  @Test
  public void sourceMdcRemappedToLabels() {
    String json = encode(Map.of(
      LoggingUtils.MDC_KEY_SOURCE, "310358",
      LoggingUtils.MDC_KEY_DATASET, "310368"
    ));

    // remapped away from the reserved ECS object field
    assertTrue(json, json.contains("\"labels.source\":\"310358\""));
    assertFalse(json, json.contains("\"source\":\"310358\""));
    // unrelated, non-reserved MDC keys are still emitted verbatim at the root
    assertTrue(json, json.contains("\"dataset\":\"310368\""));
  }
}
