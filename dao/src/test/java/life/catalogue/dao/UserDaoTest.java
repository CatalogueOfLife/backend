package life.catalogue.dao;

import life.catalogue.TestUtils;
import life.catalogue.api.model.User;
import life.catalogue.config.MailConfig;
import life.catalogue.db.mapper.UserMapper;

import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UserDaoTest extends DaoTestBase {
  UserDao dao = new UserDao(factory(), new MailConfig(), null, TestUtils.mockedBroker(), validator);

  /**
   * Settings must reach the database, not only the cached user instance they are set on.
   */
  @Test
  public void updateSettings() throws Exception {
    User u = mapper(UserMapper.class).getByUsername("test");
    commit();
    dao.updateSettings(Map.of("theme", "dark"), u);

    try (var session = factory().openSession()) {
      assertEquals(Map.of("theme", "dark"), session.getMapper(UserMapper.class).getByUsername("test").getSettings());
    }
  }
}
