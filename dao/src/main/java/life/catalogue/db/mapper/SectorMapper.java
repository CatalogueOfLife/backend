package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Sector;
import life.catalogue.api.search.SectorSearchRequest;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

public interface SectorMapper extends BaseDecisionMapper<Sector, SectorSearchRequest> {

  Sector getBySubject(@Param("datasetKey") int datasetKey,
                      @Param("key") DSID<String> key);
  
  List<Sector> listByTarget(@Param("key") DSID<String> key);

  List<Sector> listByDataset(@Param("datasetKey") @Nullable Integer datasetKey,
                             @Param("subjectDatasetKey") int subjectDatasetKey);
  
  /**
   * List all sectors which have a targetID within the given sector.
   */
  List<Sector> listChildSectors(@Param("key") DSID<Integer> sectorKey);

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

}
