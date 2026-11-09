package life.catalogue.event;

import life.catalogue.db.PgConfig;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Configuration of the event broker, see {@link PgEventBroker}.
 * The Chronicle queue settings of former versions are ignored, so old configs still parse.
 */
@JsonIgnoreProperties({"queueDir", "pollingLatency", "poolSize", "bufferSize"})
public class BrokerConfig {

  public enum Mode {
    /**
     * Postgres LISTEN/NOTIFY, shared by every app on the same database.
     */
    NOTIFY,
    /**
     * Within this JVM only, e.g. for a command line tool or a server with a database of its own.
     */
    LOCAL
  }

  @NotNull
  public Mode mode = Mode.NOTIFY;

  /**
   * Name of the broker, used in logs and the application name of its database connection.
   */
  @NotNull
  public String name = "main";

  /**
   * The postgres notification channel. Every app sharing a database must use the same one.
   */
  @NotNull
  @Pattern(regexp = "[a-z_][a-z0-9_]*")
  public String channel = "clb_events";

  /**
   * The primary database to LISTEN on. Required where the main database is a read only standby, as a standby
   * cannot LISTEN. Defaults to the main database.
   */
  @Valid
  @Nullable
  public PgConfig db;

  /**
   * Seconds between two checks that the listening connection is alive.
   */
  @Min(1)
  public int checkSeconds = 30;

  /**
   * Maximum seconds to wait between two attempts to reconnect.
   */
  @Min(1)
  public int maxReconnectSeconds = 60;

  /**
   * Seconds a listener on a standby waits for the standby to replay a change before handling its event anyway.
   */
  @Min(0)
  public int standbyWaitSeconds = 60;
}
