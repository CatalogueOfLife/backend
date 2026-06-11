package life.catalogue.dw.managed;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.common.io.UTF8IoUtils;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maintenance mode flag plus an optional custom banner message, persisted to a
 * small JSON status file that is served statically and polled by the UI:
 *
 * <pre>{"maintenance": true, "message": "..."}</pre>
 *
 * The file is the single source of truth, so the state survives server restarts
 * and the banner keeps working even while the API itself is down.
 */
public class Maintenance {
  private static final Logger LOG = LoggerFactory.getLogger(Maintenance.class);
  private final File statusFile;
  private boolean on;
  private String message;

  public Maintenance(File statusFile) {
    this.statusFile = statusFile;
    // restore state from the status file so it survives restarts
    if (statusFile != null && statusFile.exists()) {
      try {
        JsonNode n = ApiModule.MAPPER.readTree(statusFile);
        on = n.path("maintenance").asBoolean(false);
        message = n.hasNonNull("message") ? n.get("message").asText() : null;
      } catch (Exception e) {
        LOG.warn("Could not read maintenance status from {}", statusFile, e);
      }
    }
  }

  public boolean isOn() {
    return on;
  }

  /**
   * Sets (or toggles) maintenance mode and the optional custom banner message,
   * persisting both to the status file.
   *
   * @param enable explicit on/off; if {@code null} the current state is toggled
   * @param msg    optional custom banner message; blank/absent clears it
   */
  public synchronized Map<String, Object> set(Boolean enable, String msg) throws IOException {
    on = enable != null ? enable : !on;
    message = (msg != null && !msg.isBlank()) ? msg.trim() : null;
    write();
    LOG.info("Set maintenance mode={}{}", on,
      message != null ? " message=\"" + message + "\"" : "");
    return status();
  }

  public synchronized Map<String, Object> status() {
    Map<String, Object> status = new HashMap<>();
    status.put("maintenance", on);
    status.put("message", message);
    return status;
  }

  private void write() throws IOException {
    if (statusFile == null) return;
    // serialise with Jackson so a free-text message is correctly JSON-escaped
    try (Writer w = UTF8IoUtils.writerFromFile(statusFile)) {
      ApiModule.MAPPER.writeValue(w, status());
    }
  }
}
