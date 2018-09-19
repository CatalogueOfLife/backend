package org.col.es;

public class EsConfig {

  /**
   * Base name from which to construct index names for the name usage index.
   */
  public static final String NAME_USAGE_BASE = "nu";
  
  /**
   * The default name of the type created within an index.
   */
  public static final String DEFAULT_TYPE_NAME = "_doc";

  public String hosts;
  public String ports;
  public IndexConfig nameUsage;

}
