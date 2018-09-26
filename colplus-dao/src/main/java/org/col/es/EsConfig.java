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

  /**
   * Comma separated list of hosts with ES nodes
   */
  public String hosts;

  /**
   * Comma separated list of ports matching the hosts list
   */
  public String ports;

  public IndexConfig nameUsage;

  /**
   * @return true if an embedded es server should be used
   */
  public boolean embedded() {
    return hosts == null || hosts.startsWith("/");
  }
}
