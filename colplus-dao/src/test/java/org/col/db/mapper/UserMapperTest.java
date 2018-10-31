package org.col.db.mapper;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.col.api.model.ColUser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UserMapperTest extends MapperTestBase<UserMapper> {
  
  public UserMapperTest() {
    super(UserMapper.class);
  }
  
  @Test
  public void roundtrip() throws Exception {
    ColUser u1 = create("iggy");
    mapper().create(u1);
    commit();
    
    ColUser u2 = mapper().get(u1.getKey());
    // remove newly set property
    u2.setLastLogin(null);
    u2.setCreated(null);
    
    assertEquals(u1, u2);
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