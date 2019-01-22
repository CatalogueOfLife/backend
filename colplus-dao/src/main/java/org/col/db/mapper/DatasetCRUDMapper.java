package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.DatasetEntity;
import org.col.api.model.Page;

public interface DatasetCRUDMapper<V extends DatasetEntity> {
  
  V get(@Param("datasetKey") int datasetKey, @Param("id") String id);
  
  void create(V obj);
  
  int update(V obj);
  
  int delete(@Param("datasetKey") int datasetKey, @Param("id") String id);
  
  int count(@Param("datasetKey") int datasetKey);
  
  List<V> list(@Param("datasetKey") int datasetKey, @Param("page") Page page);
  
}
