package life.catalogue.db.mapper;

import life.catalogue.db.DatasetProcessable;
import org.apache.ibatis.annotations.Param;

/**
 * DatasetProcessable refers to archived dataset metadata for projects only!
 */
public interface ProjectSourceMapper extends DatasetProcessable<ProjectSourceDataset> {

  /**
   * Copies a given dataset into the archive.
   * The archive requires the source datasets key and project datasetKey combined to be unique.
   *
   * @param d dataset to store as the projects source
   */
  void create(ProjectSourceDataset d);

  /**
   * Retrieves a projects source dataset from the archive by its key and the projects datasetKey.
   * @param key the source dataset key
   * @param datasetKey the project key
   */
  ProjectSourceDataset get(@Param("key") int key, @Param("datasetKey") int datasetKey);

}
