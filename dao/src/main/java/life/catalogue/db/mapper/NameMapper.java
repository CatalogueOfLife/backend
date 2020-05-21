package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.Page;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.db.*;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * When creating a new name if the homotypic group key is not yet set the newly created name key will be
 * used to point to the name itself
 */
public interface NameMapper extends CRUD<DSID<String>, Name>, DatasetProcessable<Name>, DatasetPageable<Name>, SectorProcessable<Name>, CopyDataset {
  
  Name getByUsage(@Param("datasetKey") int datasetKey, @Param("usageId") String usageId);
  
  /**
   * Lists all distinct name index ids from the names table.
   */
  Cursor<String> processIndexIds(@Param("datasetKey") int datasetKey,
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
  List<Name> indexGroup(@Param("id") String nameId);
  
  /**
   *
   * @param datasetKey
   * @param id
   * @param nameIndexID
   */
  void updateMatch(@Param("datasetKey") int datasetKey, @Param("id") String id,
                   @Param("nameIndexID") String nameIndexID,
                   @Param("matchType") MatchType matchType
  );

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
}
