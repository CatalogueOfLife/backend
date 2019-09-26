package org.col.db;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Page;

public interface DatasetPageable<T> {
  
  List<T> list(@Param("datasetKey") int datasetKey, @Param("page") Page page);
  
  int count(@Param("datasetKey") int datasetKey);
  
}
