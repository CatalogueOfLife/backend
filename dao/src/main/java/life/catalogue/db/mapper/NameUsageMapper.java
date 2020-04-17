package life.catalogue.db.mapper;

import life.catalogue.api.model.*;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;
import org.checkerframework.checker.units.qual.K;
import org.gbif.nameparser.api.Rank;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * Mapper dealing with methods returning the NameUsage interface, i.e. a name in the context of either a Taxon, TaxonVernacularUsage,
 * Synonym or BareName.
 * <p>
 * Mapper sql should be reusing sql fragments from the 3 concrete implementations as much as possible avoiding duplication.
 */
public interface NameUsageMapper {

  NameUsageBase get(@Param("key") DSID<String> key);

  int delete(@Param("key") DSID<String> key);

  int count(@Param("datasetKey") int datasetKey);
  
  List<NameUsageBase> list(@Param("datasetKey") int datasetKey, @Param("page") Page page);
  
  List<NameUsageBase> listByNameID(@Param("datasetKey") int datasetKey, @Param("nameId") String nameId);

  List<NameUsageBase> listByNameIndexID(@Param("datasetKey") int datasetKey, @Param("nameIdxIds") List<String> nameIdxIds);

  List<NameUsageBase> listByName(@Param("datasetKey") int datasetKey,
                         @Param("name") String sciname,
                         @Nullable @Param("rank") Rank rank);

  /**
   * Lists all children (taxon & synonym) of a given parent
   * @param key the parent to list the direct children from
   * @param rank optional rank cutoff filter to only include children with a rank lower than the one given
   */
  List<NameUsageBase> children(@Param("key") DSID<String> key, @Nullable @Param("rank") Rank rank);

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
   * Does a recursive delete to remove an entire subtree including synonyms and cascading to all associated data.
   * The method does not remove name records, but returns a list of all name ids so they can be removed if needed.
   *
   * @param key root node of the subtree to delete
   * @return list of all name ids associated with the deleted taxa
   */
  List<String> deleteSubtree(@Param("key") DSID<String> key);

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
   * @param includeSynonyms if true includes synonyms, otherwise only taxa
   * @param depthFirst if true uses a depth first traversal which is more expensive then breadth first!
   */
  Cursor<NameUsageBase> processTree(@Param("datasetKey") int datasetKey,
                     @Param("sectorKey") Integer sectorKey,
                     @Param("startID") @Nullable String startID,
                     @Param("exclusions") @Nullable Set<String> exclusions,
                     @Param("lowestRank") @Nullable Rank lowestRank,
                     @Param("includeSynonyms") boolean includeSynonyms,
                     @Param("depthFirst") boolean depthFirst);
  
  /**
   * Depth first only implementation using a much lighter object then above.
   *
   * Iterates over all descendants in a tree in depth-first order for a given start taxon.
   * If the start taxon is null all root taxa are used.
   *
   * An optional exclusion filter can be used to prevent traversal of subtrees.
   * Synonyms are also traversed if includeSynonyms is true.
   *
   * @param sectorKey optional sector key to limit the traversal to
   * @param startID taxon id to start the traversal. Will be included in the result. If null start with all root taxa
   * @param exclusions set of taxon ids to exclude from traversal. This will also exclude all descendants
   * @param includeSynonyms if true includes synonyms, otherwise only taxa
   */
  Cursor<SimpleName> processTreeSimple(@Param("datasetKey") int datasetKey,
                   @Param("sectorKey") @Nullable Integer sectorKey,
                   @Param("startID") @Nullable String startID,
                   @Param("exclusions") @Nullable Set<String> exclusions,
                   @Param("lowestRank") @Nullable Rank lowestRank,
                   @Param("includeSynonyms") boolean includeSynonyms);

  /**
   * Very lightweight (sub)tree traversal that only returns the usage and name id of any descendant of the start usage.
   * Iterates over all descendants including synonyms in a tree in depth-first order for a given start taxon
   *
   * @param key taxon to start the traversal. Will be included in the result. If null start with all root taxa
   */
  Cursor<UsageNameID> processTreeIds(@Param("key") DSID<String> key);

}
