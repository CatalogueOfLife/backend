package life.catalogue.config;

import jakarta.validation.constraints.Min;

public class SyncManagerConfig {

  /**
   * Duration in minutes the sync scheduler will fall to sleep if syncs are running already.
   * Zero will turn off sync scheduling.
   */
  @Min(0)
  public int polling = 0;


}
