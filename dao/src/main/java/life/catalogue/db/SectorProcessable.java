package life.catalogue.db;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

/**
 * Minimal mapper to deal with entities of type V for an entire sector in a managed dataset.
 * @param <V> entity type
 */
public interface SectorProcessable<V> {
  
  /**
   * Iterates over all entities of a given sector in a memory friendly way, bypassing the 1st level mybatis cache.
   */
  Cursor<V> processSector(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);

  /**
   * Deletes all entities from the given sector
   * @param datasetKey dataset key of the project
   * @param sectorKey sector key of the entities to be deleted
   */
  int deleteBySector(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);

  /**
   * Updates all entities for the given sector and sets their sectorKey to NULL
   * @param datasetKey dataset key of the project
   * @param sectorKey sector key of the entities to be deleted
   */
  int removeSectorKey(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);

}