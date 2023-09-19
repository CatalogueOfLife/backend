package life.catalogue.dao;

import life.catalogue.api.event.UserPermissionChanged;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.UserMapper;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;

public class AuthorizationDao {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(AuthorizationDao.class);

  private final EventBus bus;
  private final SqlSessionFactory factory;

  public AuthorizationDao(SqlSessionFactory factory, EventBus bus) {
    this.factory = factory;
    this.bus = bus;
  }

  public List<User> listUsers(int datasetKey, User.Role role) {
    try (SqlSession session = factory.openSession()){
      UserMapper um = session.getMapper(UserMapper.class);
      if (role == User.Role.EDITOR) {
        return um.datasetEditors(datasetKey);
      } else if (role == User.Role.REVIEWER) {
        return um.datasetReviewer(datasetKey);
      }
    }
    throw new IllegalArgumentException("Unsupported role " + role);
  }

  public void addUser(int datasetKey, int userKey, User.Role role, User actor) {
    changeDatasetUserRole(datasetKey, userKey, role, actor, role == User.Role.EDITOR ?
                                                     dm -> dm.addEditor(datasetKey, userKey, actor.getKey()) :
                                                     dm -> dm.addReviewer(datasetKey, userKey, actor.getKey())
    );
  }

  public void removeUser(int datasetKey, int userKey, User.Role role, User actor) {
    changeDatasetUserRole(datasetKey, userKey, role, actor, role == User.Role.EDITOR ?
                                                     dm -> dm.removeEditor(datasetKey, userKey, actor.getKey()) :
                                                     dm -> dm.removeReviewer(datasetKey, userKey, actor.getKey())
    );
  }

  private void changeDatasetUserRole(int datasetKey, int userKey, User.Role role, User actor, Consumer<DatasetMapper> action) {
    if (role != User.Role.EDITOR && role != User.Role.REVIEWER) {
      throw new IllegalArgumentException("Role " + role + " not allowed on dataset " + datasetKey);
    }
    // we cannot change user roles for releases
    var info = DatasetInfoCache.CACHE.info(datasetKey);
    if (info.origin.isRelease()) {
      throw new IllegalArgumentException("Dataset " + datasetKey + " is a release. Use the project for user role management.");
    }

    if (!actor.isAdmin() && !actor.isEditor(datasetKey)) {
      throw new WebApplicationException(Response.Status.FORBIDDEN);
    }
    User user;
    try (SqlSession session = factory.openSession()){
      user = session.getMapper(UserMapper.class).get(userKey);
      if (user == null) {
        throw new IllegalArgumentException("User " + userKey + " does not exist");
      }
      action.accept(session.getMapper(DatasetMapper.class));
      session.commit();
    }
    bus.post(new UserPermissionChanged(user.getUsername()));
  }

}
