package org.col.db.mapper;

import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.model.Page;
import org.col.api.model.Taxon;
import org.gbif.nameparser.api.Rank;

/**
 * Taxon mapper with CRUD features.
 *
 * For create or update note that the name must exist already and taxon.name.id be present.
 */
public interface TaxonMapper extends DatasetCRUDMapper<Taxon> {

  int countRoot(@Param("datasetKey") int datasetKey);
  
  List<Taxon> listRoot(@Param("datasetKey") int datasetKey, @Param("page") Page page);
  
  List<Taxon> listByNameID(@Param("datasetKey") int datasetKey, @Param("nameId") String nameId);
  
  List<Taxon> listByName(@Param("datasetKey") int datasetKey,
                         @Param("name") String sciname,
                         @Nullable @Param("rank") Rank rank);
  
  /**
   * @return list of all parents starting with the immediate parent
   */
  List<Taxon> classification(@Param("datasetKey") int datasetKey, @Param("id") String id);
  
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
   */
  void processTree(@Param("datasetKey") int datasetKey,
                   @Param("sectorKey") Integer sectorKey,
                   @Param("startID") @Nullable  String startID,
                   @Param("exclusions") @Nullable Set<String> exclusions,
                   @Param("depthFirst") boolean depthFirst,
                   ResultHandler<Taxon> handler);
  
  /**
   * @param datasetKey the catalogue being assembled
   * @param sectorKey sector that foreign children should point into
   * @return
   */
  List<Taxon> foreignChildren(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  
  int deleteBySector(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  
  /**
   *
   * @param datasetKey the datasetKey of the catalogue
   * @param id the taxon id
   * @param dkey the datasetKey that sectors are counted for
   * @param delta the change to apply to the count for the given datasetKey, can be negative
   */
  void incDatasetSectorCount(@Param("datasetKey") int datasetKey, @Param("id") String id, @Param("dkey") int dkey, @Param("delta") int delta);
  
}
