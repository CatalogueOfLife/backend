package life.catalogue.dao;

import com.google.common.eventbus.EventBus;
import life.catalogue.api.event.UserChanged;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.model.User;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.db.mapper.UserMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;

public class UserDao extends EntityDao<Integer, User, UserMapper> {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(UserDao.class);

  private final EventBus bus;

  public UserDao(SqlSessionFactory factory, EventBus bus) {
    super(false, factory, UserMapper.class);
    this.bus = bus;
  }

  public ResultPage<User> search(@Nullable final String q, @Nullable Page page) {
    page = page == null ? new Page() : page;
    try (SqlSession session = factory.openSession()){
      UserMapper um = session.getMapper(mapperClass);
      List<User> result = um.search(q, defaultPage(page));
      return new ResultPage<>(page, result, () -> um.searchCount(q));
    }
  }

  public void updateSettings(Map<String, String> settings, User user) {
    if (user != null && settings != null) {
      user.setSettings(settings);
      try (SqlSession session = factory.openSession()){
        session.getMapper(UserMapper.class).update(user);
      }
    }
  }

  public void changeRole(int key, User admin, List<User.Role> roles) {
    User user;
    Set<User.Role> roleSet = new HashSet<User.Role>(ObjectUtils.coalesce(roles, Collections.EMPTY_SET));
    try (SqlSession session = factory.openSession()) {
      user = session.getMapper(mapperClass).get(key);
    }
    user.setRoles(roleSet);
    // if we revoke the editor role the user lost access to all datasets!
    if (!roles.contains(User.Role.EDITOR) && user.getDatasets() != null) {
      user.getDatasets().clear();
    }
    update(user, admin.getKey());
  }

  private static Page defaultPage(Page page){
    return page == null ? new Page(0, 10) : page;
  }

  @Override
  protected void createAfter(User obj, int user, UserMapper mapper, SqlSession session) {
    super.createAfter(obj, user, mapper, session);
    bus.post(UserChanged.created(obj));
  }

  @Override
  protected void updateAfter(User obj, User old, int user, UserMapper mapper, SqlSession session) {
    super.updateAfter(obj, old, user, mapper, session);
    bus.post(UserChanged.change(obj));
  }

  @Override
  protected void deleteAfter(Integer key, User old, int user, UserMapper mapper, SqlSession session) {
    super.deleteAfter(key, old, user, mapper, session);
    bus.post(UserChanged.delete(key, User.class));
  }
}
