package life.catalogue.dw.managed;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.common.io.UTF8IoUtils;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.ws.rs.InternalServerErrorException;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
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
 * A missing file is created, which needs a directory the server user can write to.
 * Otherwise the file has to exist already and be writable.
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
    if (!writable(statusFile)) {
      LOG.error("Maintenance status file {} cannot be written, setting maintenance mode will fail", statusFile);
    }
  }

  /**
   * @return true if the file can be written: an existing file has to be writable,
   * a missing one needs a writable directory to be created in.
   */
  static boolean writable(File f) {
    if (f == null) return true;
    if (f.exists()) return Files.isWritable(f.toPath());
    // the closest existing ancestor decides, missing directories are created on write
    File dir = f.getAbsoluteFile().getParentFile();
    while (dir != null && !dir.exists()) {
      dir = dir.getParentFile();
    }
    return dir != null && Files.isWritable(dir.toPath());
  }

  public boolean isOn() {
    return on;
  }

  /**
   * Sets (or toggles) maintenance mode and the optional custom banner message,
   * persisting both to the status file. The state only changes once the file is written,
   * so a failed write leaves the server reporting what the UI banner still shows.
   *
   * @param enable explicit on/off; if {@code null} the current state is toggled
   * @param msg    optional custom banner message; blank/absent clears it
   * @throws InternalServerErrorException if the status file cannot be written
   */
  public synchronized Map<String, Object> set(Boolean enable, String msg) {
    boolean newOn = enable != null ? enable : !on;
    String newMessage = (msg != null && !msg.isBlank()) ? msg.trim() : null;
    Map<String, Object> newStatus = status(newOn, newMessage);
    write(newStatus);
    on = newOn;
    message = newMessage;
    LOG.info("Set maintenance mode={}{}", on,
      message != null ? " message=\"" + message + "\"" : "");
    return newStatus;
  }

  public synchronized Map<String, Object> status() {
    return status(on, message);
  }

  private static Map<String, Object> status(boolean on, String message) {
    Map<String, Object> status = new HashMap<>();
    status.put("maintenance", on);
    status.put("message", message);
    return status;
  }

  private void write(Map<String, Object> status) {
    if (statusFile == null) return;
    // serialise with Jackson so a free-text message is correctly JSON-escaped
    try (Writer w = UTF8IoUtils.writerFromFile(statusFile)) {
      ApiModule.MAPPER.writeValue(w, status);
    } catch (IOException e) {
      String hint = statusFile.exists()
        ? "the file is not writable by the server user"
        : "the file does not exist and its directory is not writable by the server user";
      throw new InternalServerErrorException("Cannot write maintenance status file " + statusFile + ": " + hint, e);
    }
  }
}
