package life.catalogue.db.mapper;

import life.catalogue.api.model.Citation;
import life.catalogue.api.model.Dataset;

import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * Mapping source citations for all datasets, current, archived and released sources.
 */
public interface CitationMapper {

  /**
   * Create a single dataset citation.
   * @param datasetKey the dataset key the citation belongs to
   */
  void create(@Param("datasetKey") int datasetKey, @Param("obj") Citation citation);

  List<Citation> list(@Param("datasetKey") int datasetKey);

  void delete(@Param("datasetKey") int datasetKey);


  /**
   * Copies the source citations of a given dataset key into the archive using the current attempt in dataset.
   * Note that this can be done only once as the datasetKey & attempt must be unique.
   * @param datasetKey current dataset key to archive
   */
  void createArchive(@Param("datasetKey") int datasetKey);

  List<Citation> listArchive(@Param("datasetKey") int datasetKey, @Param("attempt") int attempt);

  /**
   * Deletes all attempts from the archive for a given dataset
   */
  void deleteArchive(@Param("datasetKey") int datasetKey);


  /**
   * Copies the source citations of a given project source into the source archive.
   * @param releaseKey dataset key of the release to copy to
   * @param datasetKey source dataset key the citations belong to
   * @param attempt import attempt for source dataset specifying exact version to archive
   */
  void createRelease(@Param("datasetKey") int datasetKey, @Param("releaseKey") int releaseKey, @Param("attempt") int attempt);

  List<Citation> listRelease(@Param("datasetKey") int datasetKey, @Param("releaseKey") int releaseKey);

  void deleteRelease(@Param("datasetKey") int datasetKey, @Param("releaseKey") int releaseKey);

}
