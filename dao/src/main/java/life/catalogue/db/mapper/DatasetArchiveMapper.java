package life.catalogue.db.mapper;

import life.catalogue.api.model.Dataset;
import life.catalogue.db.DatasetProcessable;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

/**
 * DatasetProcessable refers to archived dataset metadata for projects only!
 */
public interface DatasetArchiveMapper extends DatasetProcessable<ProjectSourceDataset> {

  /**
   * Copies a given dataset key into the archive.
   * @param key current dataset key to archive
   */
  void createArchive(@Param("key") int key);

  /**
   * Copies a given dataset into the archive.
   * The archive requires the 3 properties key, import attempt and the optional project datasetKey to be unique.
   *
   * @param d dataset to archive
   */
  void createProjectArchive(ProjectSourceDataset d);

  /**
   * Retrieves a dataset from the archive by its key and import attempt.
   * @param key
   * @param attempt
   */
  Dataset getArchive(@Param("key") int key, @Param("attempt") int attempt);

  /**
   * Retrieves a projects source dataset from the archive by its key, import attempt and the projects datasetKey.
   * @param key
   * @param attempt
   * @param datasetKey
   */
  ProjectSourceDataset getProjectArchive(@Param("key") int key, @Param("attempt") int attempt, @Param("datasetKey") int datasetKey);

  /**
   * Iterates over all source datasets of a given project.
   * The source dataset versions are tight to the import attempt used when a sector was actually last synced.
   * If sectors from the same source dataset are synced using different dataset import attempts, only the latest attempt is included.
   *
   * Historical dataset attempts are retrieved from the dataset archive.
   * Includes private datasets.
   *
   * See https://github.com/CatalogueOfLife/backend/issues/689 for discussion.
   *
   * @param datasetKey the projects datasetKey
   */
  Cursor<ProjectSourceDataset> processSources(@Param("datasetKey") Integer datasetKey);

}
