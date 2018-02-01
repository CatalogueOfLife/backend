package org.col.dw.config;

/**
 *
 */
public class GbifConfig {
  public String api = "https://api.gbif.org/v1/";

  /**
   * GBIF registry sync frequency in hours.
   * If zero or negative GBIF sync is off.
   */
  public int syncFrequency = 0;
}
