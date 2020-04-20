package life.catalogue.db.mapper;

import life.catalogue.api.model.Page;
import life.catalogue.api.search.ReferenceSearchRequest;
import life.catalogue.db.CRUD;
import org.apache.ibatis.annotations.Param;
import life.catalogue.api.model.User;
import life.catalogue.db.GlobalPageable;

import java.util.List;

/**
 * Mapper for users.
 * Returned users can be partially populated only to just reveal public information.
 * The standard CRUD methods do all use the full user instance.
 */
public interface UserMapper extends CRUD<Integer, User>, GlobalPageable<User> {

  /**
   * @return full user info
   */
  User getByUsername(@Param("username") String username);

  /**
   * Search for user by their user/first/last name
   * @param query string for a like search
   * @return public user infos
   */
  List<User> search(@Param("q") String query, @Param("page") Page page);

  int searchCount(@Param("q") String query);

  /**
   * Retrieves a user with its public information only
   * @return public user infos
   */
  User getPublic(@Param("key") int key);

  /**
   * Lists all editors for a given dataset with their public information only
   * @return public user infos
   */
  List<User> datasetEditors(@Param("datasetKey") int datasetKey);

}
