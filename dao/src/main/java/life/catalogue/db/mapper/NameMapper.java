package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.Page;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.db.*;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;
import org.gbif.nameparser.api.Rank;

import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * When creating a new name if the homotypic group key is not yet set the newly created name key will be
 * used to point to the name itself
 */
public interface NameMapper extends CRUD<DSID<String>, Name>, DatasetProcessable<Name>, DatasetPageable<Name>, SectorProcessable<Name>, CopyDataset {

  /**
   * Selects a number of distinct name from a single dataset by their keys
   *
   * @param ids must contain at least one value, not allowed to be empty !!!
   */
  List<Name> listByIds(@Param("datasetKey") int datasetKey, @Param("ids") Set<String> ids);

  Name getByUsage(@Param("datasetKey") int datasetKey, @Param("usageId") String usageId);

  /**
   * Lists all name ids of a dataset or sector belonging to ranks that are ambiguous in their placement
   * like section or series which are placed differently in botany and zoology.
   *
   * Our rank enum places these ranks according to their frequent botanical use below genus level.
   */
  List<String> ambiguousRankNameIds(@Param("datasetKey") int datasetKey,
                                    @Param("sectorKey") @Nullable Integer sectorKey);
  /**
   * Deletes names by sector key and a max rank to be included.
   * An optional set of name ids can be provided that will be excluded from the deletion.
   * This is useful to avoid deletion of ambiguous ranks like section or series which are placed differently in zoology and botany.
   * @param key the sector key
   * @param excludeNameIds name ids to exclude from the deletion
   */
  int deleteBySectorAndRank(@Param("key") DSID<Integer> key, @Param("rank") Rank rank, @Param("nameIds") Collection<String> excludeNameIds);

  /**
   * Iterates over all names returning the concatenation of scientific name and authorship from the names table.
   */
  Cursor<String> processNameStrings(@Param("datasetKey") int datasetKey,
                                 @Nullable @Param("sectorKey") Integer sectorKey);

  /**
   * Iterates over all names of a given dataset that have been modified since the given time and processes them with the supplied handler.
   */
  Cursor<Name> processSince(@Param("datasetKey") int datasetKey,
                    @Param("since") LocalDateTime since);

  /**
   * Lists all homotypic names based on the same homotypic name key
   *
   * @param nameId name id of the homotypic group
   */
  List<Name> homotypicGroup(@Param("datasetKey") int datasetKey, @Param("id") String nameId);
  
  /**
   * Returns the list of names published in the same reference.
   */
  List<Name> listByReference(@Param("datasetKey") int datasetKey, @Param("refId") String publishedInId);
  
  /**
   * Lists all names with the same names index key across all datasets.
   *
   * @param nameId from the names index!
   */
  List<Name> indexGroup(@Param("id") int nameId);

  /**
   * @return true if at least one record for the given dataset exists
   */
  boolean hasData(@Param("datasetKey") int datasetKey);

  /**
   * Deletes all names that do not have at least one name usage, i.e. remove all bare names.
   * @param datasetKey the datasetKey to restrict the deletion to
   * @param before optional timestamp to restrict deletions to orphans before the given time
   * @return number of deleted names
   */
  int deleteOrphans(@Param("datasetKey") int datasetKey, @Param("before") @Nullable LocalDateTime before);

  List<Name> listOrphans(@Param("datasetKey") int datasetKey,
                         @Param("before") @Nullable LocalDateTime before,
                         @Param("page") Page page);

  class NameWithNidx extends Name {
    public Integer namesIndexId;
    public MatchType namesIndexType;
  }

  Cursor<NameWithNidx> processDatasetWithNidx(@Param("datasetKey") int datasetKey);

  /**
   * Same as regular get, but includes a names index mapping
   */
  NameWithNidx getWithNidx(DSID<String> key);
}
