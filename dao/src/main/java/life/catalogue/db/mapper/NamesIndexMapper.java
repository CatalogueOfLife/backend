package life.catalogue.db.mapper;

import life.catalogue.api.model.IndexName;
import life.catalogue.db.CRUD;
import org.apache.ibatis.cursor.Cursor;

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

}
