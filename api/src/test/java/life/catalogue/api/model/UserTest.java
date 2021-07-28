package life.catalogue.api.model;

import org.junit.Test;

import static life.catalogue.api.model.User.Role.ADMIN;
import static life.catalogue.api.model.User.Role.EDITOR;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UserTest {

  @Test
  public void hasRole() {
    User u = new User();
    for (User.Role r : User.Role.values()) {
      assertFalse(u.hasRole(r));
    }

    u.addRole(EDITOR);

    assertTrue(u.hasRole(EDITOR));
    assertFalse(u.hasRole(ADMIN));

    u.addRole(ADMIN);
    assertTrue(u.hasRole(ADMIN));
    assertTrue(u.hasRole(EDITOR));
  }


  @Test
  public void isEditor() {
    User u = new User();
    assertFalse(u.isEditor(1));

    u.addDataset(1);
    assertTrue(u.isEditor(1));

    u.addRole(ADMIN);
    assertTrue(u.isEditor(1));

    u.addRole(EDITOR);
    assertTrue(u.isEditor(1));
    assertTrue(u.isEditor(1));
    assertFalse(u.isEditor(-1));
  }
}