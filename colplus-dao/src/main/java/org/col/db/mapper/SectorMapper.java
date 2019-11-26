package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.model.Sector;
import org.col.db.CRUD;
import org.col.db.DatasetPageable;

public interface SectorMapper extends CRUD<Integer, Sector>, DatasetPageable<Sector>, ProcessableDataset<Sector> {
  
  Sector getBySubject(@Param("targetDatasetKey") int targetDatasetKey,
                      @Param("subjectDatasetKey") int subjectDatasetKey,
                      @Param("id") String id);
  
  List<Sector> listByTarget(@Param("targetDatasetKey") int targetDatasetKey,
                            @Param("id") String id);

  List<Sector> listByDataset(@Param("targetDatasetKey") int targetDatasetKey,
                             @Param("subjectDatasetKey") int subjectDatasetKey);
  
  /**
   * List all sectors which have a targetID within the given sector.
   */
  List<Sector> listChildSectors(@Param("targetDatasetKey") int targetDatasetKey,
                                @Param("key") int sectorKey);

  /**
   * List all sectors that cannot anymore be linked to subject taxa in the source
   * @param targetDatasetKey the targets datasetKey
   * @param subjectDatasetKey the subjects datasetKey
   */
  List<Sector> subjectBroken(@Param("targetDatasetKey") int targetDatasetKey,
                             @Param("subjectDatasetKey") int subjectDatasetKey);
  
  /**
   * List all sectors from a source dataset that cannot anymore be linked to attachment points in a targets catalogue
   * @param targetDatasetKey the targets datasetKey
   * @param subjectDatasetKey the subjects datasetKey
   */
  List<Sector> targetBroken(@Param("targetDatasetKey") int targetDatasetKey,
                            @Param("subjectDatasetKey") int subjectDatasetKey);
  
  /**
   * Process all sectors for a given subject dataset and target catalogue
   * @param targetDatasetKey the targets datasetKey
   * @param subjectDatasetKey the subjects datasetKey
   */
  void processSectors(@Param("targetDatasetKey") int targetDatasetKey,
                      @Param("subjectDatasetKey") int subjectDatasetKey,
                      ResultHandler<Sector> handler);
  
  /**
   * Returns a list of unique dataset keys from all catalogues that have at least one sector.
   */
  List<Integer> listTargetDatasetKeys();
  
}
