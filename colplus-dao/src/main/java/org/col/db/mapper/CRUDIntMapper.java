package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.IntKey;

public interface CRUDIntMapper<T extends IntKey> extends CRUDMapper<Integer, T> {
  
  T get(@Param("key") int key);
  
  int delete(@Param("key") int key);
  
}
