package org.col.db.mapper;

import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.model.*;

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
   * Iterates over all accepted descendants in a tree in breadth-first order for a given start taxon
   * and processes them with the supplied handler. If the start taxon is null all root taxa are used.
   *
   * This allows a single query to efficiently stream all its values without keeping them in memory.
   *
   * An optional exclusion filter can be used to prevent traversal of subtrees.
   * Synonyms are not traversed, this only works on Taxa!
   *
   * @param sectorKey optional sector key to limit the traversal to
   * @param startID taxon id to start the traversal. Will be included in the result. If null start with all root taxa
   * @param exclusions set of taxon ids to exclude from traversal. This will also exclude all descendants
   * @param depthFirst if true uses a depth first traversal which is more expensive then breadth first!
   *
   * @deprecated Use NameUsageMapper.process instead to also iterate over synonyms if wanted
   */
  @Deprecated
  void processTree(@Param("datasetKey") int datasetKey,
                   @Param("sectorKey") Integer sectorKey,
                   @Param("startID") @Nullable String startID,
                   @Param("exclusions") @Nullable Set<String> exclusions,
                   @Param("depthFirst") boolean depthFirst,
                   ResultHandler<Taxon> handler);
  
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
   * Updates a single taxon sector count map by passing a delta map
   */
  void updateDatasetSectorCount(@Param("datasetKey") int datasetKey, @Param("id") String id, @Param("count") Int2IntMap count);
  
  /**
   * Sets all sector counts above species level to null
   * @param datasetKey
   */
  void resetDatasetSectorCount(@Param("datasetKey") int datasetKey);

}
