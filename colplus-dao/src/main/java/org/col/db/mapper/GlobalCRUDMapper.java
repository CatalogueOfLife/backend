package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.model.GlobalEntity;
import org.col.api.model.Page;
import org.col.db.GlobalCRUD;
import org.col.db.Pageable;

public interface GlobalCRUDMapper<V extends GlobalEntity> extends GlobalCRUD<V>, Pageable<V> {
  
  int count();
  
  List<V> list(@Param("page") Page page);
  
  /**
   * Iterates over all entities and processes them with the supplied handler.
   * This allows a single query to efficiently stream all its values without keeping them in memory.
   *
   * @param handler to process each object with
   */
  void processAll(ResultHandler<V> handler);
  
}