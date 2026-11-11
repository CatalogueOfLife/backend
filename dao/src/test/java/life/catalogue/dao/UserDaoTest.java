package life.catalogue.dao;

import life.catalogue.TestUtils;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.Users;
import life.catalogue.config.MailConfig;
import life.catalogue.db.mapper.UserMapper;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

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

  /**
   * The two organisation roles are granted and revoked independently of each other.
   */
  @Test
  public void organisationRoles() throws Exception {
    User u = mapper(UserMapper.class).getByUsername("test");
    commit();
    User admin = new User();
    admin.setKey(Users.TESTER);
    admin.addRole(User.Role.ADMIN);
    UUID org1 = UUID.randomUUID();
    UUID org2 = UUID.randomUUID();

    dao.changeOrgPublisher(u.getKey(), admin, org1, true);
    dao.changeOrgPublisher(u.getKey(), admin, org2, true);
    dao.changeOrgEditor(u.getKey(), admin, org1, true);
    User u2 = dao.get(u.getKey());
    assertEquals(Set.of(org1, org2), u2.getOrgPublisher());
    assertEquals(Set.of(org1), u2.getOrgEditor());

    dao.changeOrgPublisher(u.getKey(), admin, org1, false);
    u2 = dao.get(u.getKey());
    assertEquals(Set.of(org2), u2.getOrgPublisher());
    assertEquals(Set.of(org1), u2.getOrgEditor());

    // only admins grant organisation roles
    User editor = new User();
    editor.setKey(Users.TESTER);
    editor.addRole(User.Role.EDITOR);
    assertThrows(IllegalArgumentException.class, () -> dao.changeOrgEditor(u.getKey(), editor, org2, true));
  }
}
