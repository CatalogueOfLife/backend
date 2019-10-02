package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.DatasetIDEntity;
import org.col.db.DatasetPageable;

public interface DatasetCRUDMapper<V extends DatasetIDEntity> extends DatasetCopyMapper<V>, DatasetPageable<V> {
  
  V get(@Param("datasetKey") int datasetKey, @Param("id") String id);
  
  int update(V obj);
  
  int delete(@Param("datasetKey") int datasetKey, @Param("id") String id);
  
  /**
   * @return true if at least one record for the given dataset exists
   */
  boolean hasData(@Param("datasetKey") int datasetKey);

}
