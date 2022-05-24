package life.catalogue.db.mapper;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.db.CopyDataset;
import life.catalogue.db.SectorProcessable;
import life.catalogue.db.TempNameUsageRelated;

import org.gbif.nameparser.api.Rank;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

/**
 * Mapper dealing with methods returning the NameUsage interface, i.e. a name in the context of either a Taxon, Synonym or BareName.
 * <p>
 * Mapper sql should be reusing sql fragments from the 3 concrete implementations as much as possible avoiding duplication.
 */
public interface NameUsageMapper extends SectorProcessable<NameUsageBase>, CopyDataset, TempNameUsageRelated {

  NameUsageBase get(@Param("key") DSID<String> key);

  /**
   * SimpleName.parent=parentName
   * @param key
   * @return
   */
  SimpleName getSimple(@Param("key") DSID<String> key);

  List<SimpleName> findSimple(@Param("datasetKey") int datasetKey,
                              @Param("sectorKey") Integer sectorKey,
                              @Param("status") TaxonomicStatus status,
                              @Param("rank") Rank rank,
                              @Param("name") String name
  );

  /**
   * Retrieves a simple name usage from a project by using the stable usage ID
   * and the id map table
   * @param key usage key with dataset being the project and ID being the stable id
   * @return simple name with parent keys being the stable id, resolved via the id map
   */
  SimpleName getSimpleByIdMap(@Param("key") DSID<String> key);

  boolean exists(@Param("key") DSID<String> key);

  int delete(@Param("key") DSID<String> key);

  int count(@Param("datasetKey") int datasetKey);

  List<SimpleName> listByRegex(@Param("datasetKey") int datasetKey,
                               @Param("regex") String regex,
                               @Param("status") TaxonomicStatus status,
                               @Param("rank") Rank rank,
                               @Param("page") Page page);

  List<NameUsageBase> list(@Param("datasetKey") int datasetKey, @Param("page") Page page);

  /**
   * Returns related name usages based on the same name as matched against the names index.
   *
   * @param key the key of any name usage
   * @param datasetKeys optional filter by target dataset keys
   * @param publisherKey optional filter by a target GBIF publisher key
   * @return
   */
  List<NameUsageBase> listRelated(@Param("key") DSID<String> key,
                                  @Param("datasetKeys") @Nullable Collection<Integer> datasetKeys,
                                  @Param("publisherKey") @Nullable UUID publisherKey);

  /**
   * Warning, this does not return bare names, only true usages!
   */
  List<NameUsageBase> listByNameID(@Param("datasetKey") int datasetKey, @Param("nameId") String nameId, @Param("page") Page page);

  /**
   * Warning, this does not return bare name IDs, only true usage IDs!
   */
  List<String> listUsageIDsByNameID(@Param("datasetKey") int datasetKey, @Param("nameId") String nameId);

  /**
   * Warning, this does not return bare names, only true usages!
   */
  List<NameUsageBase> listByNamesIndexID(@Param("datasetKey") int datasetKey, @Param("nidx") int nidx, @Param("page") Page page);

  /**
   * Warning, this does not return bare names, only true usages!
   */
  List<NameUsageBase> listByNamesIndexIDGlobal(@Param("nidx") int nidx, @Param("page") Page page);

  /**
   * Warning, this does not count bare names, only true usages!
   */
  Integer countByNamesIndexID(@Param("nidx") int nidx, @Nullable @Param("datasetKey") Integer datasetKey);

  List<NameUsageBase> listByName(@Param("datasetKey") int datasetKey,
                         @Param("name") String sciname,
                         @Param("rank") @Nullable Rank rank, @Param("page") Page page);

  /**
   * Lists all children (taxon & synonym) of a given parent
   * @param key the parent to list the direct children from
   * @param rank optional rank cutoff filter to only include children with a rank lower than the one given
   */
  List<NameUsageBase> children(@Param("key") DSID<String> key, @Nullable @Param("rank") Rank rank);

  /**
   * Iterates over all usages for a given dataset, optionally filtered by a minimum/maximum rank to include.
   */
  Cursor<NameUsageBase> processDataset(@Param("datasetKey") int datasetKey,
                                       @Nullable @Param("minRank") Rank minRank,
                                       @Nullable @Param("maxRank") Rank maxRank);

  /**
   * Iterates over all bare names for a given dataset, optionally filtered by a minimum/maximum rank to include.
   */
  Cursor<BareName> processDatasetBareNames(@Param("datasetKey") int datasetKey,
                                       @Nullable @Param("minRank") Rank minRank,
                                       @Nullable @Param("maxRank") Rank maxRank);

  /**
   * Move all children including synonyms of a given taxon to a new parent.
   * @param datasetKey
   * @param parentId the current parentId
   * @param newParentId the new parentId
   * @param sectorKey the optional sectorKey to restrict the update to
   * @return number of changed usages
   */
  int updateParentIds(@Param("datasetKey") int datasetKey,
                      @Param("parentId") String parentId,
                      @Param("newParentId") String newParentId,
                      @Param("sectorKey") @Nullable Integer sectorKey,
                      @Param("userKey") int userKey);

  /**
   * Moves the taxon given to a new parent by updating the parent_id
   * @param key the taxon to update
   * @param parentId the new parentId to assign
   */
  void updateParentId(@Param("key") DSID<String> key,
                      @Param("parentId") String parentId,
                      @Param("userKey") int userKey);

  /**
   * Creates a new temp table for usage & name ids in the current transaction.
   * The table will be dropped immediately if the transaction is committed, otherwise at the end of the session.
   */
  void createTempTable();

  /**
   * Add usage and name ids for all synonyms of a given sector to the sectors temp table.
   * Make sure the temp table was created in the session before!
   */
  void addSectorSynonymsToTemp(@Param("key") DSID<Integer> key);

  /**
   * Add usage and name ids for all usages of a given sector and below or equal a max rank to the sessions temp table.
   * Make sure the temp table was created in the session before!
   *
   * @param key the sector key
   * @param rank max rank to be included, higher ranks will not be deleted
   */
  void addSectorBelowRankToTemp(@Param("key") DSID<Integer> key, @Param("rank") Rank rank);

  void removeFromTemp(@Param("nameID") String nameID);

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
   * @param lowestRank optional rank cutoff filter to only include children with a rank above or equal to the one given
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
   * List all usages from a sector different to the one given including nulls,
   * which are direct children of a taxon from the given sector key.
   *
   * Returned SimpleName instances have the parentID as their parent property, not a scientificName!
   *
   * @param sectorKey sector that foreign children should point into
   */
  List<SimpleName> foreignChildren(@Param("key") DSID<Integer> sectorKey);

  /**
   * List all taxa of a project/release that are the root of a given sector by looking at the real usages.
   * For ATTACH sectors this should just be one, but for UNIONs it is likely multiple.
   */
  List<SimpleName> sectorRoot(@Param("key") DSID<Integer> sectorKey);

  /**
   * Depth first only implementation using a much lighter object then above.
   *
   * Iterates over all descendants in a tree in depth-first order for a given start taxon.
   * If the start taxon is null all root taxa are used.
   *
   * An optional exclusion filter can be used to prevent traversal of subtrees.
   * Synonyms are also traversed if includeSynonyms is true.
   *
   * Processed SimpleName instances have the parentID as their parent property, not a scientificName!
   *
   * @param sectorKey optional sector key to limit the traversal to
   * @param startID taxon id to start the traversal. Will be included in the result. If null start with all root taxa
   * @param exclusions set of taxon ids to exclude from traversal. This will also exclude all descendants
   * @param lowestRank optional rank cutoff filter to only include children with a rank above or equal to the one given
   * @param includeSynonyms if true includes synonyms, otherwise only taxa
   */
  Cursor<SimpleName> processTreeSimple(@Param("datasetKey") int datasetKey,
                   @Param("sectorKey") @Nullable Integer sectorKey,
                   @Param("startID") @Nullable String startID,
                   @Param("exclusions") @Nullable Set<String> exclusions,
                   @Param("lowestRank") @Nullable Rank lowestRank,
                   @Param("includeSynonyms") boolean includeSynonyms);

  /**
   * Very lightweight (sub)tree traversal that only returns the usage and name id of the start usage and any descendant of the start usage.
   * Iterates over all descendants including synonyms in a tree in depth-first order for a given start taxon
   *
   * @param key taxon to start the traversal. Will be included in the result
   */
  Cursor<UsageNameID> processTreeIds(@Param("key") DSID<String> key);

  /**
   * Iterate over all usages ordered by their canonical names index id.
   */
  Cursor<SimpleNameWithNidx> processNxIds(@Param("datasetKey") int datasetKey);

  Cursor<String> processIds(@Param("datasetKey") int datasetKey);
}
