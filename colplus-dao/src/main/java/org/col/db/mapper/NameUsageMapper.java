package org.col.db.mapper;

import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.model.NameUsageBase;
import org.col.api.model.Page;
import org.gbif.nameparser.api.Rank;

/**
 * Mapper dealing with methods returning the NameUsage interface, i.e. a name in the context of either a Taxon, TaxonVernacularUsage,
 * Synonym or BareName.
 * <p>
 * Mapper sql should be reusing sql fragments from the 3 concrete implementations as much as possible avoiding duplication.
 */
public interface NameUsageMapper {
  
  int count(@Param("datasetKey") int datasetKey);
  
  List<NameUsageBase> list(@Param("datasetKey") int datasetKey, @Param("page") Page page);
  
  List<NameUsageBase> listByNameID(@Param("datasetKey") int datasetKey, @Param("nameId") String nameId);
  
  List<NameUsageBase> listByName(@Param("datasetKey") int datasetKey,
                         @Param("name") String sciname,
                         @Nullable @Param("rank") Rank rank);
  
  /**
   * Move all children including synonyms of a given taxon to a new parent.
   * @param datasetKey
   * @param parentId the current parentId
   * @param newParentId the new parentId
   * @return number of changed usages
   */
  int updateParentId(@Param("datasetKey") int datasetKey,
                     @Param("parentId") String parentId,
                     @Param("newParentId") String newParentId,
                     @Param("userKey") int userKey);
  
  int deleteBySector(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  
  /**
   * Iterates over all accepted descendants in a tree in breadth-first order for a given start taxon
   * and processes them with the supplied handler. If the start taxon is null all root taxa are used.
   *
   * This allows a single query to efficiently stream all its values without keeping them in memory.
   *
   * An optional exclusion filter can be used to prevent traversal of subtrees.
   * Synonyms are also traversed if includeSynonyms is true.
   *
   * @param sectorKey optional sector key to limit the traversal to
   * @param startID taxon id to start the traversal. Will be included in the result. If null start with all root taxa
   * @param exclusions set of taxon ids to exclude from traversal. This will also exclude all descendants
   * @param depthFirst if true uses a depth first traversal which is more expensive then breadth first!
   */
  void processTree(@Param("datasetKey") int datasetKey,
                   @Param("sectorKey") Integer sectorKey,
                   @Param("startID") @Nullable String startID,
                   @Param("exclusions") @Nullable Set<String> exclusions,
                   @Param("includeSynonyms") boolean includeSynonyms,
                   @Param("depthFirst") boolean depthFirst,
                   ResultHandler<NameUsageBase> handler);
  
}
