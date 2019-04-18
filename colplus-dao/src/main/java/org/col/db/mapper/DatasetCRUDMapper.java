package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.model.DatasetEntity;
import org.col.api.model.Page;
import org.col.db.DatasetCRUD;

public interface DatasetCRUDMapper<V extends DatasetEntity> extends DatasetCRUD<V> {
  
  int count(@Param("datasetKey") int datasetKey);
  
  List<V> list(@Param("datasetKey") int datasetKey, @Param("page") Page page);
  
  /**
   * Iterates over all names of a given dataset and processes them with the supplied handler.
   * This allows a single query to efficiently stream all its values without keeping them in memory.
   *
   * @param handler to process each name with
   */
  void processDataset(@Param("datasetKey") int datasetKey, ResultHandler<V> handler);
  
}
