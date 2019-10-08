package org.col.db;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.DataEntity;

public interface CRUD<K, V extends DataEntity<K>> {

  void create(V obj);

  V get(@Param("key") K key);
  
  int update(V obj);
  
  int delete(@Param("key") K key);

}
