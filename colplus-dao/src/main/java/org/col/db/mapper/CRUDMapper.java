package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;

public interface CRUDMapper<K, V> {
  
  V get(@Param("key") K key);
  
  void create(V obj);
  
  int update(V obj);
  
  /**
   * Marks a source as deleted
   *
   * @param key
   */
  int delete(@Param("key") K key);
  
}
