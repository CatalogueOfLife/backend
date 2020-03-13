package life.catalogue.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

/**
 * Minimal mapper that allows to create new entities and stream read them for an entire dataset.
 */
public interface ProcessableDataset<V> {
  
  /**
   * Iterates over all entities of a given dataset in a memory friendly way, bypassing the 1st level mybatis cache.
   */
  Cursor<V> processDataset(@Param("datasetKey") int datasetKey);
  
}