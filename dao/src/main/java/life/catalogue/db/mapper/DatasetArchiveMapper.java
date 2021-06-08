package life.catalogue.db.mapper;

import life.catalogue.api.model.Dataset;
import life.catalogue.db.DatasetProcessable;

import org.apache.ibatis.annotations.Param;

public interface DatasetArchiveMapper extends DatasetProcessable<Dataset> {

  /**
   * Copies a given dataset key into the archive.
   * Note that this can be done only once as the datasetKey & attempt must be unique.
   * @param key current dataset key to archive
   */
  void create(@Param("key") int key);

  /**
   * Retrieves a dataset from the archive by its key and import attempt.
   * @param key
   * @param attempt
   */
  Dataset get(@Param("key") int key, @Param("attempt") int attempt);

}
