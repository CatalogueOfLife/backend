package life.catalogue.db.mapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import life.catalogue.api.RandomUtils;
import life.catalogue.api.model.User;
import org.junit.Test;

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
  public void datasetEditors() throws Exception {
    final int dkAll = 1000;
    final int dkEven = 1001;
    List<Integer> userKeys = new ArrayList<>();
    for (int x = 1; x<=10; x++) {
      User u = createTestEntity();
      u.setDeleted(null);
      u.addDataset(dkAll);
      if (x % 2 == 0) {
        u.addDataset(dkEven);
      }
      mapper().create(u);
      userKeys.add(u.getKey());
    }
    commit();

    assertEquals(10, mapper().datasetEditors(dkAll).size());
    assertEquals(5, mapper().datasetEditors(dkEven).size());
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