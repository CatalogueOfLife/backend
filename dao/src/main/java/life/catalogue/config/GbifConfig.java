package life.catalogue.config;

import java.util.Set;
import java.util.UUID;

import com.google.common.base.MoreObjects;

import jakarta.validation.constraints.NotNull;

import life.catalogue.api.vocab.Publishers;

/**
 *
 */
public class GbifConfig {

  @NotNull
  public String api = "https://api.gbif.org/v1/";

  /**
   * Registry sync user that needs registry_admin rights to write back to the registry
   */
  public String username;
  public String password;

  /**
   * If true the registry sync will also write back dataset keys to the GBIF registry
   * and add missing datasets.
   */
  public boolean bidirectional = false;

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

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
      .add("api", api)
      .add("user", username)
      .add("bidirectional", bidirectional)
      .add("syncFrequency", syncFrequency)
      .toString();
  }
}
