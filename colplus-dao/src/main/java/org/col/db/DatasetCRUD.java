package org.col.db;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.DatasetEntity;

public interface DatasetCRUD<V extends DatasetEntity> {
  
  V get(@Param("datasetKey") int datasetKey, @Param("id") String id);
  
  void create(V obj);
  
  int update(V obj);
  
  int delete(@Param("datasetKey") int datasetKey, @Param("id") String id);
  
}
