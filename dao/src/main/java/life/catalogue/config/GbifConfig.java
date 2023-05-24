package life.catalogue.config;

import javax.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

/**
 *
 */
public class GbifConfig {
  public static final UUID PLAZI_KEY = UUID.fromString("7ce8aef0-9e92-11dc-8738-b8a03c50a862");

  @NotNull
  public String api = "https://api.gbif.org/v1/";

  /**
   * GBIF registry sync frequency in minutes.
   * If zero or negative GBIF sync is off.
   */
  public int syncFrequency = 0;

  /**
   * GBIF publisher keys for journals and other publishers who exclusively publish article based datasets, e.g. Plazi & Pensoft.
   */
  @NotNull
  public Set<UUID> articlePublishers = Set.of(PLAZI_KEY);

}
