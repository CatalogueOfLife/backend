package org.col.db.mapper;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.col.api.RandomUtils;
import org.col.api.model.ColUser;
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
  public void roundtrip() throws Exception {
    ColUser u1 = createTestEntity();
    mapper().create(u1);
    commit();
    
    removeDbCreatedProps(u1);
    ColUser u2 = removeDbCreatedProps(mapper().get(u1.getKey()));
    //printDiff(u1, u2);
    assertEquals(u1, u2);
  }
  
  @Test
  public void update() throws Exception {
    ColUser u1 = createTestEntity();
    mapper().create(u1);
    commit();
  
    u1.setFirstname("Peter Punk");
    mapper().update(u1);
    commit();
    
    removeDbCreatedProps(u1);
    ColUser u2 = removeDbCreatedProps(mapper().get(u1.getKey()));
    
    //printDiff(u1, u2);
    assertEquals(u1, u2);
  }
  
  @Test
  public void deleted() throws Exception {
    ColUser u1 = createTestEntity();
    mapper().create(u1);
    commit();
    
    mapper().delete(u1.getKey());
    commit();
    
    assertNull(mapper().get(u1.getKey()));
  }
  
  
  ColUser createTestEntity() {
    return create(RandomUtils.randomLatinString(10));
  }
  
  ColUser removeDbCreatedProps(ColUser obj) {
    obj.setLastLogin(null);
    obj.setCreated(null);
    return obj;
  }
  
  ColUser create(String username) {
    ColUser iggy = new ColUser();
    iggy.setUsername(username);
    iggy.setFirstname("James");
    iggy.setLastname("Osterberg");
    iggy.setEmail("iggy@mailinator.com");
    iggy.setOrcid("0000-0000-0000-0666");
    iggy.setRoles(Arrays.stream(ColUser.Role.values()).collect(Collectors.toSet()));
    iggy.getSettings().put("foo", "bar");
    return iggy;
  }
}