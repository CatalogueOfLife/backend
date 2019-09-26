package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.model.CatalogueEntity;
import org.col.db.DatasetPageable;
import org.col.db.GlobalCRUD;

/**
 * datasetKey parameters here (see DatasetPageable) are the catalogues datasetKey
 */
public interface CatalogueCRUDMapper<V extends CatalogueEntity> extends GlobalCRUD<V>, DatasetPageable<V> {
  
  
  /**
   * Iterates over all entities of a given dataset and processes them with the supplied handler.
   * This allows a single query to efficiently stream all its values without keeping them in memory.
   *
   * @param datasetKey the catalogues datasetKey
   * @param handler to process each entity with
   */
  void processCatalogue(@Param("datasetKey") int datasetKey, ResultHandler<V> handler);
  
}