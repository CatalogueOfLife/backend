package org.col.es;

public class IndexConfig {

  /**
   * The name of the index and the type within the index (since Elasticsearch has deprecated the
   * creation of multiple types within a single index, we can use the same name for both the index
   * and the type).
   */
  public String name;
  /**
   * The model class corresponding to the type.
   */
  public String modelClass;
  public String numShards;
  public String numReplicas;

}
