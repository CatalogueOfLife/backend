package org.col.es;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class EsConfig {
  
  /**
   * Name of the Elasticsearch index for name usages
   */
  public static final String ES_INDEX_NAME_USAGE = "nu";
  
  /**
   * The default name of the type created within an index.
   */
  static final String DEFAULT_TYPE_NAME = "_doc";
  
  /**
   * Environment to prefix indices with to be able to share a single ES instance with multiple CoL+ installations.
   * prod or dev are sensible values.
   */
  public String environment;

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
  @JsonIgnore
  public boolean embedded() {
    return hosts == null || hosts.startsWith("/");
  }
  
  /**
   * @return the index name prefixed with the configured environment
   */
  public String indexName(String name) {
    return environment == null ? name : environment + "-" + name;
  }
}
