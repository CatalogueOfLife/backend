package life.catalogue.config;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import life.catalogue.api.vocab.Publishers;

/**
 *
 */
public class GbifConfig {

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
  public Set<UUID> articlePublishers = Set.of(Publishers.PLAZI);

}
