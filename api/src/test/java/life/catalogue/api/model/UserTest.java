package life.catalogue.api.model;

import org.junit.Test;

import static life.catalogue.api.model.User.Role.ADMIN;
import static life.catalogue.api.model.User.Role.EDITOR;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UserTest {

  @Test
  public void isAdmin() {
    User u = new User();
    assertTrue(u.getRoles().isEmpty());
    assertFalse(u.isAdmin());

    u.getRoles().add(ADMIN);
    assertFalse(u.getRoles().isEmpty());
    assertTrue(u.isAdmin());
  }

  @Test
  public void isEditor() {
    User u = new User();
    assertFalse(u.isEditor(1));

    u.addDatasetRole(EDITOR, 1);
    assertTrue(u.isEditor(1));

    u.getRoles().add(ADMIN);
    assertTrue(u.isEditor(1));

    assertTrue(u.isEditor(1));
    assertTrue(u.isEditor(1));
    assertFalse(u.isEditor(-1));
  }
}