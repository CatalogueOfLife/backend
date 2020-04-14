package life.catalogue.db.mapper;

import org.apache.ibatis.annotations.Param;
import life.catalogue.api.model.User;
import life.catalogue.db.GlobalPageable;

import java.util.List;

/**
 *
 */
public interface UserMapper extends GlobalPageable<User> {
  
  User getByUsername(@Param("username") String username);
  
  void create(User obj);

  /**
   * Retrieves a full user with all data
   */
  User get(@Param("key") int key);

  /**
   * Retrieves a user with its public information only
   */
  User getPublic(@Param("key") int key);

  int update(User obj);
  
  int delete(@Param("key") int key);

  /**
   * Lists all editors for a given dataset with their public information only
   */
  List<User> datasetEditors(@Param("datasetKey") int datasetKey);

}
