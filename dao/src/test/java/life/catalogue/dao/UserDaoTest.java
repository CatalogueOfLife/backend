package life.catalogue.dao;

import life.catalogue.api.model.User;

import org.junit.Test;

import static org.junit.Assert.*;

public class UserDaoTest {

  @Test
  public void buildEmailText() throws Exception {
    var user = new User();
    // required props
    user.setKey(999);
    user.setUsername("freerk");
    user.setFirstname("Freerk");
    user.setLastname("Buse");
    user.setOrcid("0000-0002-3530-013X");
    var text = UserDao.buildEmailText(user, "Please give me editor permissions - I am great!");
    System.out.println(text);
  }
}