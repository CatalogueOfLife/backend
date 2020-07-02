package life.catalogue.db.mapper;

import life.catalogue.api.model.ArchivedDataset;
import life.catalogue.db.DatasetProcessable;
import org.apache.ibatis.annotations.Param;

/**
 * DatasetProcessable refers to archived dataset metadata for projects only!
 */
public interface DatasetArchiveMapper extends DatasetProcessable<ArchivedDataset> {

  /**
   * Copies a given dataset key into the archive.
   * @param key current dataset key to archive
   */
  void create(@Param("key") int key);

  /**
   * Retrieves a dataset from the archive by its key and import attempt.
   * @param key
   * @param attempt
   */
  ArchivedDataset get(@Param("key") int key, @Param("attempt") int attempt);

}
