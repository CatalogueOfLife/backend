package org.col.db;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.IntKey;
import org.col.db.mapper.Pageable;

public interface CRUDInt<T extends IntKey> extends Pageable<T>, CRUD<Integer, T> {
  
  T get(@Param("key") int key);
  
  int delete(@Param("key") int key);
  
}
