package org.col.db.mapper;

import java.util.List;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.apache.ibatis.annotations.Param;
import org.col.api.model.Page;
import org.col.api.model.SimpleName;
import org.col.api.model.Taxon;
import org.col.api.model.TaxonCountMap;

/**
 * Mapper dealing only with accepted name usages, i.e. Taxon instances.
 */
public interface TaxonMapper extends DatasetCRUDMapper<Taxon> {
  
  int countRoot(@Param("datasetKey") int datasetKey);
  
  List<Taxon> listRoot(@Param("datasetKey") int datasetKey, @Param("page") Page page);
  
  /**
   * @return list of all parents starting with the immediate parent
   */
  List<Taxon> classification(@Param("datasetKey") int datasetKey, @Param("id") String id);
  
  /**
   * Same as classification but only returning minimal simple name objects
   * @return list of all parents starting with the immediate parent
   */
  List<SimpleName> classificationSimple(@Param("datasetKey") int datasetKey, @Param("id") String id);
  
  List<TaxonCountMap> classificationCounts(@Param("datasetKey") int datasetKey, @Param("id") String id);
  
  /**
   * @return the sector count map for a given taxon
   */
  TaxonCountMap getCounts(@Param("datasetKey") int datasetKey, @Param("id") String id);
  
  int countChildren(@Param("datasetKey") int datasetKey, @Param("id") String id);
  
  List<Taxon> children(@Param("datasetKey") int datasetKey, @Param("id") String id, @Param("page") Page page);
  
  /**
   * @param datasetKey the catalogue being assembled
   * @param sectorKey sector that foreign children should point into
   * @return
   */
  List<Taxon> foreignChildren(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  
  /**
   * @return set of sector keys that are targeting a descendant of the given root taxon id
   */
  List<Integer> listSectors(@Param("datasetKey") int datasetKey, @Param("id") String id);

  /**
   * Recursively updates the sector count for a given taxon and all its parents.
   * @param datasetKey the datasetKey of the catalogue
   * @param id the taxon id
   * @param dkey the datasetKey that sectors are counted for
   * @param delta the change to apply to the count for the given datasetKey, can be negative
   */
  void incDatasetSectorCount(@Param("datasetKey") int datasetKey, @Param("id") String id, @Param("dkey") int dkey, @Param("delta") int delta);
  
  /**
   * Updates a single taxon sector count map
   */
  void updateDatasetSectorCount(@Param("datasetKey") int datasetKey, @Param("id") String id, @Param("count") Int2IntOpenHashMap count);
  
  /**
   * Sets all sector counts above species level to null
   * @param datasetKey
   */
  void resetDatasetSectorCount(@Param("datasetKey") int datasetKey);

}
