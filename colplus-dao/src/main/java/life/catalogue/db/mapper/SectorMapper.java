package life.catalogue.db.mapper;

import java.util.List;

import life.catalogue.api.search.SectorSearchRequest;
import life.catalogue.db.Searchable;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import life.catalogue.api.model.Sector;
import life.catalogue.db.CRUD;
import life.catalogue.db.DatasetPageable;

public interface SectorMapper extends CRUD<Integer, Sector>, DatasetPageable<Sector>, ProcessableDataset<Sector>, Searchable<Sector, SectorSearchRequest> {
  
  Sector getBySubject(@Param("datasetKey") int datasetKey,
                      @Param("subjectDatasetKey") int subjectDatasetKey,
                      @Param("id") String id);
  
  List<Sector> listByTarget(@Param("datasetKey") int datasetKey,
                            @Param("id") String id);

  List<Sector> listByDataset(@Param("datasetKey") int datasetKey,
                             @Param("subjectDatasetKey") int subjectDatasetKey);
  
  /**
   * List all sectors which have a targetID within the given sector.
   */
  List<Sector> listChildSectors(@Param("targetDatasetKey") int targetDatasetKey,
                                @Param("key") int sectorKey);
  
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
