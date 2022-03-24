package life.catalogue.db.mapper;

import life.catalogue.api.model.IndexName;
import life.catalogue.api.model.Page;
import life.catalogue.db.CRUD;

import org.gbif.nameparser.api.Rank;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;

/**
 * When creating a new name if the homotypic group key is not yet set the newly created name key will be
 * used to point to the name itself
 */
public interface NamesIndexMapper extends CRUD<Integer, IndexName> {

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

}
