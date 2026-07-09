package life.catalogue.db.mapper;

import life.catalogue.api.model.IndexName;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.SimpleName;
import life.catalogue.db.CRUD;

import org.gbif.nameparser.api.Rank;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

/**
 * When creating a new name if the homotypic group key is not yet set the newly created name key will be
 * used to point to the name itself
 */
public interface NamesIndexMapper extends CRUD<Integer, IndexName> {

  /**
   * Atomically inserts a new canonical entry keyed by its normalized bucket key, or does nothing if a row
   * with that normalized key already exists (INSERT ... ON CONFLICT(normalized) DO NOTHING). On a fresh
   * insert the generated primary key is set on the passed IndexName; on a conflict the key is left untouched
   * (typically null) so the caller can resolve the winner via {@link #getKeyByNormalized(String)}.
   * This makes assign-on-miss safe across concurrent index rebuilds sharing one postgres.
   */
  void createOnConflict(IndexName name);

  /**
   * @return the primary key of the names_index row with the given normalized bucket key, or null if none exists.
   */
  Integer getKeyByNormalized(@Param("normalized") String normalized);

  /**
   * Iterate through all name index entries.
   */
  Cursor<IndexName> processAll();

  /**
   * @return number of all names index entries.
   */
  int count();

  /**
   * Clears the entire names index table
   */
  void truncate();

  /**
   * Resets the primary key sequence to the next highest int or 1 if no records exist.
   */
  void updateSequence();

  /**
   * Query the names index with a regular expression pattern
   * @param regex
   * @param rank
   * @param page
   */
  List<IndexName> listByRegex(@Param("regex") String regex,
                              @Param("canonical") boolean canonical,
                              @Param("rank") Rank rank,
                              @Param("page") Page page);

  /**
   * Iterate through all name index entries as simple names with aggregated ids (@@ concatenation) from sources.
   * @param datasetKeys list of datasets to include in the result
   * @param minDatasets minimum number of datasets in which the name appears
   */
  Cursor<SimpleName> processDatasets(@Param("datasetKeys") List<Integer> datasetKeys, @Param("minDatasets") Integer minDatasets );

}
