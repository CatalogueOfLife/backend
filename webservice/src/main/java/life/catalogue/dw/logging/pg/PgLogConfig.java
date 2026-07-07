package life.catalogue.dw.logging.pg;

import life.catalogue.db.PgConfig;

import javax.annotation.Nullable;

import jakarta.validation.constraints.Min;

public class PgLogConfig {

  /**
   * Optional dedicated, writable database connection used to persist the collected logs.
   * Needed on the read-only server whose main db connection points at a read-only standby
   * and therefore cannot insert into api_logs. When null the application's main MyBatis
   * session factory is used (as on the read-write server).
   */
  @Nullable
  public PgConfig db;

  /**
   * The size of the in memory log collector queue
   */
  @Min(3)
  public int maxSize = 10_000;

  /**
   * Maximum time in seconds that is allowed before logs are persisted. If 0 only the size matters.
   * Otherwise what event ever happens first.
   */
  public int maxTime = 60*60;  // defaults to hourly

}
