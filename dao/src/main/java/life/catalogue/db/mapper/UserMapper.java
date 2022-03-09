package life.catalogue.db.mapper;

import life.catalogue.api.model.Page;
import life.catalogue.api.model.User;
import life.catalogue.db.CRUD;
import life.catalogue.db.GlobalPageable;

import java.time.LocalDateTime;
import java.util.List;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;

/**
 * Mapper for users.
 * Returned users can be partially populated only to just reveal public information.
 * The standard CRUD methods do all use the full user instance.
 */
public interface UserMapper extends CRUD<Integer, User>, GlobalPageable<User> {

  /**
   * To unblock set blocked to null
   *
   * @param key user key
   */
  void block(@Param("key") int key, @Param("blocked") @Nullable LocalDateTime blocked);

  /**
   * @return full user info
   */
  User getByUsername(@Param("username") String username);

  /**
   * Search for user by their user/first/last name
   * @param query string for a like search
   * @return public user infos
   */
  List<User> search(@Param("q") String query, @Param("role") User.Role role, @Param("page") Page page);

  int searchCount(@Param("q") String query, @Param("role") User.Role role);

  /**
   * Lists all editors for a given dataset with their public information only
   * @return public user infos
   */
  List<User> datasetEditors(@Param("datasetKey") int datasetKey);

  List<User> datasetReviewer(@Param("datasetKey") int datasetKey);
}
