package org.col.db;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.GlobalEntity;

public interface GlobalCRUD<V extends GlobalEntity> {
  
  V get(@Param("key") Integer key);
  
  void create(V obj);
  
  int update(V obj);
  
  int delete(@Param("key") Integer key);
  
}
