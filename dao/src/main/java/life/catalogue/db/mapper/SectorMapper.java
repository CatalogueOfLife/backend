package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Sector;
import life.catalogue.api.search.SectorSearchRequest;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

public interface SectorMapper extends BaseDecisionMapper<Sector, SectorSearchRequest> {

  Sector.Mode getMode(@Param("datasetKey") int datasetKey,
                      @Param("id") int id);
  Sector getBySubject(@Param("datasetKey") int datasetKey,
                      @Param("key") DSID<String> key);
  
  List<Sector> listByTarget(@Param("key") DSID<String> key);

  List<Sector> listByDataset(@Param("datasetKey") @Nullable Integer datasetKey,
                             @Param("subjectDatasetKey") @Nullable Integer subjectDatasetKey);

  List<Sector> listByDatasetPublisher(@Param("datasetKey") Integer datasetKey,
                                      @Param("publisherKey") UUID publisherKey);

  /**
   * List all distinct project dataset keys that have at least one decision on the given subject dataset key.
   * @param subjectDatasetKey the decision subjects datasetKey
   */
  List<Integer> listProjectKeys(@Param("subjectDatasetKey") int subjectDatasetKey);

  /**
   * List all sectors which have a targetID within the given sector.
   */
  List<Sector> listChildSectors(@Param("key") DSID<Integer> sectorKey);

  /**
   * List all sectors for a given dataset and mode ordered by priority starting with the lowest priority and sorting nulls last.
   * @param datasetKey project or release key
   * @param modes modes to include in the sector listing. If null all modes will be considered
   */
  List<Sector> listByPriority(@Param("datasetKey") Integer datasetKey, @Param("mode") Sector.Mode... modes);

  /**
   * List all sector keys which have a targetID within the given subtree starting with ad including the given key.
   */
  List<Integer> listDescendantSectorKeys(@Param("key") DSID<String> key);

  /**
   * Process all sectors for a given subject dataset and target catalogue
   * @param datasetKey the targets datasetKey
   * @param subjectDatasetKey the subjects datasetKey
   */
  Cursor<Sector> processSectors(@Param("datasetKey") int datasetKey,
                        @Param("subjectDatasetKey") int subjectDatasetKey);

  /**
   * Returns a list of unique sector keys from the project and all it's releases.
   * @param projectKey dataset key of the project
   */
  List<Integer> listSectorKeys(@Param("datasetKey") int projectKey);

  /**
   * Returns a list of unique dataset keys from all catalogues that have at least one sector.
   */
  List<Integer> listTargetDatasetKeys();

  /**
   * Updates the last sync attempt column of the given sector
   * and the dataset_attempt column using the current last attempt from the source dataset.
   * @param key sector key to update
   * @param attempt
   */
  int updateLastSync(@Param("key") DSID<Integer> key, @Param("attempt") int attempt);

  /**
   * Updates the sync and dataset_attempt column of the given sector in the release
   * with attempt values from the project.
   * @param key sector key in the project
   * @param releaseKey release dataset key
   * @return number of changed records
   */
  int updateReleaseAttempts(@Param("key") DSID<Integer> key, @Param("rkey") int releaseKey);

  /**
   * Checks whether we already have a sector in the given dataset with the given priority
   * @param datasetKey project or release
   * @param priority priority to test
   * @return the primary key of the sector if found
   */
  Sector getByPriority(@Param("datasetKey") Integer datasetKey, @Param("priority") Integer priority);

  /**
   * Increases the priority value of all sectors in the given dataset which have the same or a higher priority value currently.
   * A higher priority value means it will be less important!
   * @param datasetKey
   * @param priority
   * @return number of changed sectors
   */
  int incLowerPriorities(@Param("datasetKey") Integer datasetKey, @Param("priority") Integer priority);

  /**
   * Delete sectors that are not referenced from any usage, name or reference records
   * @param datasetKey project or release
   * @return number of deleted sectors
   */
  int deleteOrphans(@Param("datasetKey") Integer datasetKey);
}
