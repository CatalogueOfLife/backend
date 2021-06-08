package life.catalogue.db.mapper;

import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.Users;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
      u.setDeleted(null);
      if (x % 2 == 0) {
        u.setFirstname(john);
      }
      mapper().create(u);
      userKeys.add(u.getKey());
    }
    commit();

    Page page = new Page(0, 100);
    assertEquals(0, mapper().search("Peter", page).size());
    assertEquals(10, mapper().search(john, page).size());
    assertEquals(1, mapper().search("13", page).size());
    assertEquals(1, mapper().search("user13", page).size());
    assertEquals(20, mapper().search("user", page).size());
    assertEquals(11, mapper().search("user1", page).size());
    assertEquals(2, mapper().search("user2", page).size());
    assertEquals(26, mapper().search("", page).size());
  }

  @Test
  public void datasetEditors() throws Exception {
    DatasetMapper dm = mapper(DatasetMapper.class);

    List<Integer> all = new ArrayList<>();
    List<Integer> even = new ArrayList<>();
    for (int x = 1; x<=10; x++) {
      User u = createTestEntity();
      u.setDeleted(null);
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
    u1.getDatasets().addAll(List.of(1,2,3));
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
  
  @Test
  public void deleted() throws Exception {
    User u1 = createTestEntity();
    mapper().create(u1);
    commit();
    
    mapper().delete(u1.getKey());
    commit();
    
    assertNull(mapper().get(u1.getKey()));
  }
  
  
  User createTestEntity() {
    return create(RandomUtils.randomLatinString(10));
  }
  
  User removeDbCreatedProps(User obj) {
    obj.setLastLogin(null);
    obj.setCreated(null);
    obj.getDatasets().clear();
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