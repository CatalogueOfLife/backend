package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.db.*;

import org.gbif.api.vocabulary.NomenclaturalStatus;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;
import javax.ws.rs.QueryParam;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

/**
 * When creating a new name if the homotypic group key is not yet set the newly created name key will be
 * used to point to the name itself
 */
public interface NameMapper extends CRUD<DSID<String>, Name>, DatasetProcessable<Name>, DatasetPageable<Name>, SectorProcessable<Name>,
  CopyDataset, TempNameUsageRelated {

  /**
   * Selects a number of distinct name from a single dataset by their keys
   *
   * @param ids must contain at least one value, not allowed to be empty !!!
   */
  List<Name> listByIds(@Param("datasetKey") int datasetKey, @Param("ids") Set<String> ids);

  String getNameIdByUsage(@Param("datasetKey") int datasetKey, @Param("usageId") String usageId);

  Name getByUsage(@Param("datasetKey") int datasetKey, @Param("usageId") String usageId);

  /**
   * Lists all name ids of a dataset or sector belonging to ranks that are UNRANKED or OTHER
   * and which can be placed anywhere in the classification.
   */
  List<String> unrankedRankNameIds(@Param("datasetKey") int datasetKey,
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
   * Iterates over the names that are missing a name match record of a given dataset.
   */
  Cursor<Name> processDatasetWithoutMatches(@Param("datasetKey") int datasetKey);

  /**
   * Iterates over all names returning the concatenation of scientific name and authorship from the names table.
   */
  Cursor<String> processNameStrings(@Param("datasetKey") int datasetKey,
                                 @Nullable @Param("sectorKey") Integer sectorKey);

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

  class NameSearchRequest {
    @QueryParam("sectorKey") Integer sectorKey;
    @QueryParam("status") NomenclaturalStatus status;
    @QueryParam("rank") Rank rank;
    @QueryParam("type") NameType type;
    @QueryParam("name") String namePrefix;
    @QueryParam("matchType") MatchType matchType;
    @QueryParam("hasMatch") Boolean hasMatch;
  }
  List<Name> search(@Param("datasetKey") int datasetKey,
                    @Param("req") NameSearchRequest filter,
                    @Param("page") Page page
  );
}
