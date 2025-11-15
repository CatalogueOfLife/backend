package life.catalogue.config;

import life.catalogue.api.vocab.Publishers;

import java.util.Set;
import java.util.UUID;

import com.google.common.base.MoreObjects;

import jakarta.validation.constraints.NotNull;

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
   * Full publisher GBIF registry sync frequency in minutes.
   * If zero or negative sync is off.
   */
  public int publisherSyncFrequency = 60*24;

  /**
   * GBIF publisher keys for journals and other publishers who exclusively publish article based datasets, e.g. Plazi & Pensoft.
   */
  @NotNull
  public Set<UUID> articlePublishers = Set.of(Publishers.PLAZI);

  /**
   * GBIF installation keys for hosts that exclusively publish article based datasets, e.g. Plazi.
   */
  @NotNull
  public Set<UUID> articleHostInstallations = Set.of(UUID.fromString("7ce8aef1-9e92-11dc-8740-b8a03c50a999"));

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
