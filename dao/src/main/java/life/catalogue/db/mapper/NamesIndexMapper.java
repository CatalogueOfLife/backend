package life.catalogue.db.mapper;

import life.catalogue.api.model.NameIndexEntry;
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
public interface NamesIndexMapper extends CRUD<Integer, NameIndexEntry> {

  /**
   * Atomically inserts a new canonical entry keyed by its normalized bucket key, or does nothing if a row
   * with that normalized key already exists (INSERT ... ON CONFLICT(normalized) DO NOTHING). On a fresh
   * insert the generated primary key is set on the passed NameIndexEntry; on a conflict the key is left
   * untouched (typically null) so the caller can resolve the winner via {@link #getKeyByNormalized(String)}.
   * This makes assign-on-miss safe across concurrent index rebuilds sharing one postgres.
   */
  void createOnConflict(NameIndexEntry name);

  /**
   * @return the primary key of the names_index row with the given normalized bucket key, or null if none exists.
   */
  Integer getKeyByNormalized(@Param("normalized") String normalized);

  /**
   * Iterate through all name index entries.
   */
  Cursor<NameIndexEntry> processAll();

  /**
   * Iterate through all name index entries with an id strictly greater than the given minimum,
   * i.e. the delta appended since a previous load up to and including {@code min}.
   * Used by {@link life.catalogue.matching.nidx.NameIndexImpl#start()} to catch up an existing,
   * persistent names index store with rows that were added to postgres since it was last stopped,
   * without having to reload the entire (potentially huge) table.
   * @param min the highest id already held, exclusive
   */
  Cursor<NameIndexEntry> processSince(@Param("min") int min);

  /**
   * @return number of all names index entries.
   */
  int count();

  /**
   * @return the highest id of all names index entries, or zero if the table is empty.
   */
  int maxKey();

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
  List<NameIndexEntry> listByRegex(@Param("regex") String regex,
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
