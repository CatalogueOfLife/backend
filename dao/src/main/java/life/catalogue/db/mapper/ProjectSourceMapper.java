package life.catalogue.db.mapper;

import life.catalogue.api.model.Dataset;

import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * DatasetProcessable refers to archived dataset metadata for projects only!
 * Contrary to its name the table project_source only stores sources for releases.
 * The live project sources are determined based on the sector mappings alone and are the reason for the name of the mapper.
 */
public interface ProjectSourceMapper {

  /**
   * Copies a given dataset into the project archive.
   * The archive requires the source datasets key and project datasetKey combined to be unique.
   *
   * @param datasetKey the project the source dataset belongs to
   * @param d dataset to store as the projects source
   */
  default void create(int datasetKey, Dataset d){
    createInternal(new DatasetWithProjectKey(datasetKey, d));
  }

  void createInternal(DatasetWithProjectKey d);

  /**
   * Retrieves a single released source dataset from the archive
   * @param key the source dataset key
   * @param datasetKey the release dataset key. No project keys allowed!
   */
  Dataset getReleaseSource(@Param("key") int key, @Param("datasetKey") int datasetKey);

  /**
   * Retrieves a single source dataset for a project, reading either from the latest version
   * or the dataset archive if the last successful sync attempt was older.
   * @param key the source dataset key
   * @param datasetKey the project dataset key. No release keys allowed!
   */
  Dataset getProjectSource(@Param("key") int key, @Param("datasetKey") int datasetKey);

  /**
   * @param datasetKey the release dataset key
   */
  List<Dataset> listReleaseSources(@Param("datasetKey") int datasetKey);

  /**
   * Lists all project or release sources retrieving metadata either from the latest version
   * or an archived copy depending on the import attempt of the last sync stored in the sectors.
   */
  List<Dataset> listProjectSources(@Param("datasetKey") int datasetKey);

  /**
   * Deletes all source datasets for the given release
   * @param datasetKey the release dataset key. No project keys allowed!
   */
  int deleteByRelease(@Param("datasetKey") int datasetKey);


  class DatasetWithProjectKey {
    public final int datasetKey;
    public final Dataset dataset;

    public DatasetWithProjectKey(int projectKey, Dataset dataset) {
      this.datasetKey = projectKey;
      this.dataset = dataset;
    }
  }

}
