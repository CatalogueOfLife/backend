package life.catalogue.dao;

import life.catalogue.api.event.UserChanged;
import life.catalogue.api.model.User;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.config.MailConfig;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.UserMapper;
import life.catalogue.event.EventBroker;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.simplejavamail.api.mailer.Mailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import jakarta.validation.Validator;

public class UserDao extends UserCrudDao {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(UserDao.class);
  private final EventBroker broker;
  @Nullable
  private Mailer mailer;
  private final MailConfig mailCfg;

  public UserDao(SqlSessionFactory factory, MailConfig cfg, @Nullable Mailer emailer, EventBroker broker, Validator validator) {
    super(factory, validator);
    this.broker = broker;
    this.mailer = emailer;
    this.mailCfg = cfg;
  }

  public void updateSettings(Map<String, String> settings, User user) {
    if (user != null && settings != null) {
      user.setSettings(settings);
      try (SqlSession session = factory.openSession()){
        session.getMapper(UserMapper.class).update(user);
      }
    }
  }

  /**
   * Updates a users set of roles. Dataset specific access control lists are unchanged.
   */
  public void changeRoles(int key, User admin, List<User.Role> roles) {
    Preconditions.checkArgument(admin.isAdmin());
    User user = getOr404(key);
    final var newRoles = new HashSet<User.Role>(ObjectUtils.coalesce(roles, Collections.EMPTY_SET));
    // update user
    user.setRoles(newRoles);
    update(user, admin.getKey());
  }

  /**
   * Removes the specified user and role from all datasets held currently by the user.
   * It does not change the role of the user in general, but modifies the dataset ACLs.
   * @param key
   * @param admin
   * @param role
   */
  public void revokeRoleOnAllDatasets(int key, User admin, User.Role role) {
    Preconditions.checkArgument(admin.isAdmin());
    User user = getOr404(key);
    try (SqlSession session = factory.openSession(true)) {
      var dm = session.getMapper(DatasetMapper.class);
      if (role == User.Role.EDITOR) {
        dm.removeEditorEverywhere(user.getKey(), admin.getKey());
      } else if (role == User.Role.REVIEWER) {
        dm.removeReviewerEverywhere(user.getKey(), admin.getKey());
      } else {
        throw new IllegalArgumentException("Role " + role + " does not exist on datasets");
      }
    }
  }

  public void block(int key, User admin) {
    block(key, LocalDateTime.now(), admin);
  }

  public void unblock(int key, User admin) {
    block(key, null, admin);
  }

  private void block(int key, @Nullable LocalDateTime datetime, User admin) {
    User u;
    try (SqlSession session = factory.openSession(true)){
      var um = session.getMapper(UserMapper.class);
      um.block(key, datetime);
      u = um.get(key);
    }
    broker.publish(UserChanged.created(u, admin.getKey()));
  }


  @Override
  protected boolean createAfter(User obj, int user, UserMapper mapper, SqlSession session) {
    session.close();
    broker.publish(UserChanged.created(obj, user));
    return false;
  }

  @Override
  protected boolean updateAfter(User obj, User old, int user, UserMapper mapper, SqlSession session, boolean keepSessionOpen) {
    if (!keepSessionOpen) {
      session.close();
    }
    broker.publish(UserChanged.changed(obj, user));
    return keepSessionOpen;
  }

  @Override
  protected boolean deleteAfter(Integer key, User old, int user, UserMapper mapper, SqlSession session) {
    broker.publish(UserChanged.deleted(old, user));
    return false;
  }

  public void addReleaseKeys(User user) {
    var keys = releaseKeys(user.getKey(), user.getEditor());
    user.getEditor().addAll(keys);

    keys = releaseKeys(user.getKey(), user.getReviewer());
    user.getReviewer().addAll(keys);
  }

  /**
   * Returns all (x)release dataset keys that belong to a project included in the given projectKeys.
   * @param projectKeys dataset keys of projects. Other dataset keys, e.g. external, are ignored
   */
  private IntSet releaseKeys(int userKey, IntSet projectKeys){
    IntSet keys = new IntOpenHashSet();
    if (projectKeys != null) {
      try (SqlSession session = factory.openSession()) {
        var dm = session.getMapper(DatasetMapper.class);
        for (int projKey : projectKeys) {
          if (DatasetInfoCache.CACHE.info(projKey, true).origin == DatasetOrigin.PROJECT) {
            var res = dm.listReleaseKeys(projKey);
            if (res != null) {
              keys.addAll(res);
            }
          }
        }
      } catch (Throwable e) {
        LOG.warn("Failed to list release keys for user {}", e, userKey);
      }
    }
    return keys;
  }

}
