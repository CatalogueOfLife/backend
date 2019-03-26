package org.col.db;

import org.apache.ibatis.annotations.Param;

public interface CRUD<K, V> {
  
  V get(@Param("key") K key);
  
  void create(V obj);
  
  int update(V obj);
  
  int delete(@Param("key") K key);
  
}
