package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.model.DatasetEntity;
import org.col.db.DatasetCRUD;
import org.col.db.DatasetPageable;

public interface DatasetCRUDMapper<V extends DatasetEntity> extends DatasetCRUD<V>, DatasetPageable<V> {
  
  /**
   * @return true if at least one record for the given dataset exists
   */
  boolean hasData(@Param("datasetKey") int datasetKey);
  
  /**
   * Iterates over all entities of a given dataset and processes them with the supplied handler.
   * This allows a single query to efficiently stream all its values without keeping them in memory.
   *
   * @param datasetKey the datasets datasetKey
   * @param handler to process each entity with
   */
  void processDataset(@Param("datasetKey") int datasetKey, ResultHandler<V> handler);
  
}
