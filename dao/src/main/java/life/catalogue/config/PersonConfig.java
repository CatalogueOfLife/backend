package life.catalogue.config;

import java.io.File;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The person registry: its caches and its harvest.
 */
public class PersonConfig {

  /** citation keys whose person ids are kept in memory */
  @Min(0)
  public int keyCacheSize = 100_000;

  /** persons kept in memory */
  @Min(0)
  public int personCacheSize = 50_000;

  /** minutes a cached key or person is kept at most, in case a PersonsChanged event was missed */
  @Min(1)
  public int cacheExpireMinutes = 60;

  /** where a harvest caches the answers of its sources: kept after a failed run for the next to resume from */
  @NotNull
  public File harvestDir = new File("/tmp/col/person-harvest");

  /** days between harvests the cron executor starts, 0 for none */
  @Min(0)
  public int harvestIntervalDays = 0;
}
