package life.catalogue.db.mapper;

import org.apache.ibatis.annotations.Param;
import life.catalogue.api.model.User;
import life.catalogue.db.GlobalPageable;

import java.util.List;

/**
 *
 */
public interface UserMapper extends GlobalPageable<User> {

  /**
   * @return full user info
   */
  User getByUsername(@Param("username") String username);

  /**
   * Search for user by their user/first/last name
   * @param query string for a like search
   * @return public user infos for the first 50 matches
   */
  List<User> search(@Param("q") String query);

  void create(User obj);

  /**
   * Retrieves a full user with all data
   * @return full user info
   */
  User get(@Param("key") int key);

  /**
   * Retrieves a user with its public information only
   * @return public user infos
   */
  User getPublic(@Param("key") int key);

  int update(User obj);
  
  int delete(@Param("key") int key);

  /**
   * Lists all editors for a given dataset with their public information only
   * @return public user infos
   */
  List<User> datasetEditors(@Param("datasetKey") int datasetKey);

}
