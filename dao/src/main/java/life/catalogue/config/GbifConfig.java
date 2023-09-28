package life.catalogue.config;

import java.util.Set;
import java.util.UUID;

import javax.validation.constraints.NotNull;

/**
 *
 */
public class GbifConfig {
  public static final UUID PLAZI_KEY = UUID.fromString("7ce8aef0-9e92-11dc-8738-b8a03c50a862");

  @NotNull
  public String api = "https://api.gbif.org/v1/";

  /**
   * Incremental GBIF registry sync frequency in minutes.
   * Incremental syncs only look at changes for the day and cannot spot deleted datasets in GBIF.
   * If zero or negative GBIF sync is off.
   */
  public int syncFrequency = 0;

  /**
   * Full GBIF registry sync frequency in days.
   * If zero or negative, full GBIF syncs are off.
   *
   * Full syncs are needed to delete datasets and fix any potentially incrementally aggregated problems.
   * The default is daily.
   */
  public int fullSyncFrequency = 1;

  /**
   * GBIF publisher keys for journals and other publishers who exclusively publish article based datasets, e.g. Plazi & Pensoft.
   */
  @NotNull
  public Set<UUID> articlePublishers = Set.of(PLAZI_KEY);

}
