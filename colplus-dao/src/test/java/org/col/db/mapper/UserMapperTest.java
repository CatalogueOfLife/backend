package org.col.db.mapper;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.col.api.RandomUtils;
import org.col.api.model.ColUser;

public class UserMapperTest extends GlobalCRUDMapperTest<ColUser, UserMapper> {
  
  public UserMapperTest() {
    super(UserMapper.class);
  }
  
  @Override
  ColUser createTestEntity() {
    return create(RandomUtils.randomLatinString(10));
  }
  
  @Override
  ColUser removeDbCreatedProps(ColUser obj) {
    obj.setLastLogin(null);
    obj.setCreated(null);
    return obj;
  }
  
  @Override
  void updateTestObj(ColUser obj) {
    obj.setFirstname("Peter Punk");
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