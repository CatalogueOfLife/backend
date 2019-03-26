package org.col.db;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.IntKey;

public interface CRUDInt<T extends IntKey> extends CRUD<Integer, T> {
  
  T get(@Param("key") int key);
  
  int delete(@Param("key") int key);
  
}
