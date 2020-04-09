package life.catalogue.db.mapper;

import org.apache.ibatis.annotations.Param;
import life.catalogue.api.model.ColUser;
import life.catalogue.db.GlobalPageable;

import java.util.List;

/**
 *
 */
public interface UserMapper extends GlobalPageable<ColUser> {
  
  ColUser getByUsername(@Param("username") String username);
  
  void create(ColUser obj);

  /**
   * Retrieves a full user with all data
   */
  ColUser get(@Param("key") int key);

  /**
   * Retrieves a user with its public information only
   */
  ColUser getPublic(@Param("key") int key);

  int update(ColUser obj);
  
  int delete(@Param("key") int key);

  /**
   * Lists all editors for a given dataset with their public information only
   */
  List<ColUser> datasetEditors(@Param("datasetKey") int datasetKey);

}
