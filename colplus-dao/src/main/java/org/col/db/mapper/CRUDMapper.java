package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.IntKey;

public interface CRUDMapper<T extends IntKey> {
  
  T get(@Param("key") int key);
  
  void create(T obj);
  
  int update(T obj);
  
  /**
   * Marks a source as deleted
   *
   * @param key
   */
  int delete(@Param("key") int key);
  
}
