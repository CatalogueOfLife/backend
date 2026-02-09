package life.catalogue.db.mapper;

import life.catalogue.api.model.DOI;
import life.catalogue.api.model.Dataset;
import life.catalogue.db.DatasetProcessable;

import org.apache.ibatis.annotations.Param;

public interface DatasetArchiveMapper extends DatasetProcessable<Dataset>, DatasetAgentMapper {

  /**
   * Copies a given dataset key into the archive.
   * Source citations are NOT copied, this must be done using the CitationMapper!
   * Note that this can be done only once as the datasetKey & attempt must be unique.
   * @param key current dataset key to archive
   */
  void create(@Param("key") int key);

  /**
   * Updates the DOI of a dataset version in the archive.
   * @param key
   * @param attempt
   * @param doi
   */
  void updateVersionDOI(@Param("key") int key, @Param("attempt") int attempt, @Param("doi") DOI doi);

  /**
   * Retrieves a dataset from the archive by its key and import attempt.
   * @param key
   * @param attempt
   */
  Dataset get(@Param("key") int key, @Param("attempt") int attempt);

}
