package org.col.db.mapper;

import java.util.List;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.apache.ibatis.annotations.Param;
import org.col.api.model.*;
import org.col.db.CRUD;
import org.col.db.DatasetPageable;
import org.gbif.nameparser.api.Rank;

/**
 * Mapper dealing only with accepted name usages, i.e. Taxon instances.
 */
public interface TaxonMapper extends CRUD<DSID<String>, Taxon>, ProcessableDataset<Taxon>, DatasetPageable<Taxon> {
  
  int countRoot(@Param("datasetKey") int datasetKey);
  
  List<Taxon> listRoot(@Param("datasetKey") int datasetKey, @Param("page") Page page);
  
  /**
   * @return list of all parents starting with the immediate parent
   */
  List<Taxon> classification(@Param("key") DSID<String> key);
  
  /**
   * Same as classification but only returning minimal simple name objects
   * @return list of all parents starting with the immediate parent
   */
  List<SimpleName> classificationSimple(@Param("key") DSID<String> key);
  
  List<TaxonCountMap> classificationCounts(@Param("key") DSID<String> key);
  
  /**
   * @return the sector count map for a given taxon
   */
  TaxonCountMap getCounts(@Param("key") DSID<String> key);
  
  int countChildren(@Param("key") DSID<String> key);
  
  int countChildrenWithRank(@Param("key") DSID<String> key, @Param("rank") Rank rank);
  
  List<Taxon> children(@Param("key") DSID<String> key, @Param("page") Page page);
  
  /**
   * @param datasetKey the catalogue being assembled
   * @param sectorKey sector that foreign children should point into
   * @return
   */
  List<Taxon> foreignChildren(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  
  /**
   * Recursively updates the sector count for a given taxon and all its parents.
   * @param key the taxon datasetKey & id
   * @param dkey the datasetKey that sectors are counted for
   * @param delta the change to apply to the count for the given datasetKey, can be negative
   */
  void incDatasetSectorCount(@Param("key") DSID<String> key, @Param("dkey") int dkey, @Param("delta") int delta);
  
  /**
   * Updates a single taxon sector count map
   */
  void updateDatasetSectorCount(@Param("key") DSID<String> key, @Param("count") Int2IntOpenHashMap count);
  
  /**
   * Sets all sector counts above species level to null
   * @param datasetKey
   */
  void resetDatasetSectorCount(@Param("datasetKey") int datasetKey);

}
