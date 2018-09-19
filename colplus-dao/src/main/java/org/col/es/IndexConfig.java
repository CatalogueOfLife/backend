package org.col.es;

public class IndexConfig {

  /**
   * The model class corresponding to the type.
   */
  public String modelClass;
  public String numShards;
  public String numReplicas;
  /**
   * Batch size for bulk request
   */
  public int batchSize = 1000;

}
