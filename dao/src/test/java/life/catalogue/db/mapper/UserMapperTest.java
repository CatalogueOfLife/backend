package life.catalogue.db.mapper;

import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.Users;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.ibatis.binding.BindingException;
import org.junit.Test;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import static org.junit.Assert.*;

public class UserMapperTest extends MapperTestBase<UserMapper> {

  public UserMapperTest() {
    super(UserMapper.class);
  }

  @Test
  public void getNull() throws Exception {
    assertNull(mapper().get(-34567));
  }

  @Test
  public void search() throws Exception {
    final String john = "John";
    List<Integer> userKeys = new ArrayList<>();
    for (int x = 1; x<=20; x++) {
      User u = createTestEntity();
      u.setUsername("user"+x);
      if (x < 3) {
        u.setRoles(Set.of(User.Role.ADMIN));
      }
      if (x >= 10) {
        u.setRoles(Set.of(User.Role.REVIEWER));
      }
      if (x % 2 == 0) {
        u.setFirstname(john);
      }

      mapper().create(u);
      userKeys.add(u.getKey());
    }
    commit();

    Page page = new Page(0, 100);
    assertEquals(0, mapper().search("Peter", null, page).size());
    assertEquals(10, mapper().search(john, null, page).size());
    assertEquals(1, mapper().search("13", null, page).size());
    assertEquals(1, mapper().search("user13", null, page).size());
    assertEquals(20, mapper().search("user", null, page).size());
    assertEquals(11, mapper().search("user1", null, page).size());
    assertEquals(2, mapper().search("user2", null, page).size());
    assertEquals(28, mapper().search("", null, page).size());
    mapper().search("", null, page).forEach(u -> {
      assertNotNull(u.getKey());
      assertNotNull(u.getUsername());
      assertNotNull(u.getCreated());
    });
    assertEquals(8, mapper().search("", User.Role.EDITOR, page).size());
    assertEquals(18, mapper().search("", User.Role.REVIEWER, page).size());
    assertEquals(11, mapper().search("", User.Role.ADMIN, page).size());
    // last login persistency
    var login = mapper().getByUsername("user8");
    final var now = LocalDateTime.now();
    login.setLastLogin(now);
    mapper().update(login);
    commit();

    mapper().search("user8", null, page).forEach(u -> {
      assertNotNull(u.getKey());
      assertNotNull(u.getUsername());
      assertNotNull(u.getCreated());
      assertEquals(now, u.getLastLogin());
    });

    var u2 = mapper().getByUsername("user8");
    assertEquals(now, u2.getLastLogin());
  }

  @Test
  public void datasetEditors() throws Exception {
    DatasetMapper dm = mapper(DatasetMapper.class);

    List<Integer> all = new ArrayList<>();
    List<Integer> even = new ArrayList<>();
    for (int x = 1; x<=10; x++) {
      User u = createTestEntity();
      mapper().create(u);
      all.add(u.getKey());
      if (x % 2 == 0) {
        even.add(u.getKey());
      }
    }

    Dataset d1 = TestEntityGenerator.newDataset("all");
    d1.applyUser(Users.TESTER);
    dm.create(d1);
    dm.updateEditors(d1.getKey(), new IntOpenHashSet(all), Users.TESTER);

    Dataset d2 = TestEntityGenerator.newDataset("even");
    d2.applyUser(Users.TESTER);
    dm.create(d2);
    dm.updateEditors(d2.getKey(), new IntOpenHashSet(even), Users.TESTER);

    commit();

    assertEquals(10, mapper().datasetEditors(d1.getKey()).size());
    assertEquals(5, mapper().datasetEditors(d2.getKey()).size());
  }

  @Test
  public void roundtrip() throws Exception {
    User u1 = createTestEntity();
    u1.getEditor().addAll(List.of(1,2,3));
    mapper().create(u1);
    commit();

    removeDbCreatedProps(u1);
    User u2 = removeDbCreatedProps(mapper().get(u1.getKey()));
    //printDiff(u1, u2);
    assertEquals(u1, u2);
  }

  @Test
  public void update() throws Exception {
    User u1 = createTestEntity();
    mapper().create(u1);
    commit();

    u1.setFirstname("Peter Punk");
    mapper().update(u1);
    commit();

    removeDbCreatedProps(u1);
    User u2 = removeDbCreatedProps(mapper().get(u1.getKey()));

    //printDiff(u1, u2);
    assertEquals(u1, u2);
  }

  /**
   * We don't offer a delete method!!!
   */
  @Test(expected = BindingException.class)
  public void deleted() throws Exception {
    mapper().delete(1);
  }


  @Test
  public void block() throws Exception {
    User u = createTestEntity();
    mapper().create(u);
    commit();
    final int key = u.getKey();

    mapper().block(key, LocalDateTime.now());
    u = mapper().get(key);
    assertTrue(u.isBlockedUser());

    mapper().block(key, null);
    u = mapper().get(key);
    assertFalse(u.isBlockedUser());
  }

  User createTestEntity() {
    return create(RandomUtils.randomLatinString(10));
  }

  User removeDbCreatedProps(User obj) {
    obj.setLastLogin(null);
    obj.setCreated(null);
    obj.getEditor().clear();
    return obj;
  }

  User create(String username) {
    User iggy = new User();
    iggy.setUsername(username);
    iggy.setFirstname("James");
    iggy.setLastname("Osterberg");
    iggy.setEmail("iggy@mailinator.com");
    iggy.setOrcid("0000-0000-0000-0666");
    iggy.setRoles(Arrays.stream(User.Role.values()).collect(Collectors.toSet()));
    iggy.getSettings().put("foo", "bar");
    return iggy;
  }
}