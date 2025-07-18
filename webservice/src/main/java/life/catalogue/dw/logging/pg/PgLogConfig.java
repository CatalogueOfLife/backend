package life.catalogue.dw.logging.pg;

import jakarta.validation.constraints.Min;

public class PgLogConfig {
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
