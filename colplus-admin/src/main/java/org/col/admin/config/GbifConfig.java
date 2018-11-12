package org.col.admin.config;

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
  
  /**
   * If false just updates existing datasets,
   * if true inserts missing checklists from GBIF
   */
  public boolean insert = false;
}
