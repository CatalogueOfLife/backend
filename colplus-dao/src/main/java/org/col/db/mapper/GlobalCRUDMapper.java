package org.col.db.mapper;

import org.apache.ibatis.session.ResultHandler;
import org.col.api.model.GlobalEntity;
import org.col.db.GlobalCRUD;
import org.col.db.GlobalPageable;

public interface GlobalCRUDMapper<V extends GlobalEntity> extends GlobalCRUD<V>, GlobalPageable<V> {
  
  /**
   * Iterates over all entities and processes them with the supplied handler.
   * This allows a single query to efficiently stream all its values without keeping them in memory.
   *
   * @param handler to process each object with
   */
  void processAll(ResultHandler<V> handler);
  
}