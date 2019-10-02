package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;

/**
 * Minimal mapper that allows to create new entities and stream read them for an entire dataset.
 */
public interface DatasetCopyMapper<V> {
  
  void create(V obj);
  
  /**
   * Iterates over all entities of a given dataset and processes them with the supplied handler.
   */
  void processDataset(@Param("datasetKey") int datasetKey, ResultHandler<V> handler);
  
}