package life.catalogue.dw.auth;

import life.catalogue.api.model.User;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.TestDataRule;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static life.catalogue.api.model.User.Role.ADMIN;
import static life.catalogue.api.model.User.Role.EDITOR;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AuthFilterIT {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.datasetMix();

  @Test
  public void isAuthorized() {
    User u = new User();
    assertFalse(AuthFilter.hasReadAccess(u,10));
    assertFalse(AuthFilter.hasReadAccess(u,11));
    assertFalse(AuthFilter.hasReadAccess(u,12));
    assertFalse(AuthFilter.hasReadAccess(u,13));
    assertFalse(AuthFilter.hasReadAccess(u,20));

    u.addDatasetRole(EDITOR, 1); // also adds editor role
    assertFalse(AuthFilter.hasReadAccess(u,10));
    assertFalse(AuthFilter.hasReadAccess(u,11));
    assertFalse(AuthFilter.hasReadAccess(u,12));
    assertFalse(AuthFilter.hasReadAccess(u,13));
    assertFalse(AuthFilter.hasReadAccess(u,20));

    u.addRole(ADMIN);
    assertTrue(AuthFilter.hasReadAccess(u,10));
    assertTrue(AuthFilter.hasReadAccess(u,11));
    assertTrue(AuthFilter.hasReadAccess(u,12));
    assertTrue(AuthFilter.hasReadAccess(u,13));
    assertTrue(AuthFilter.hasReadAccess(u,20));

    u.getRoles().remove(ADMIN);
    u.addDatasetRole(EDITOR, 20);
    assertFalse(AuthFilter.hasReadAccess(u,10));
    assertFalse(AuthFilter.hasReadAccess(u,11));
    assertFalse(AuthFilter.hasReadAccess(u,12));
    assertFalse(AuthFilter.hasReadAccess(u,13));
    assertTrue(AuthFilter.hasReadAccess(u,20));

    u.addDatasetRole(EDITOR, 12); // releases should not be added to permissions, but we make sure here it has no effect
    assertFalse(AuthFilter.hasReadAccess(u,10));
    assertFalse(AuthFilter.hasReadAccess(u,11));
    assertFalse(AuthFilter.hasReadAccess(u,12));
    assertFalse(AuthFilter.hasReadAccess(u,13));
    assertTrue(AuthFilter.hasReadAccess(u,20));

    u.addDatasetRole(EDITOR, 10); // projects give access to all releases
    u.removeDatasetRole(EDITOR, 20);
    assertTrue(AuthFilter.hasReadAccess(u,10));
    assertTrue(AuthFilter.hasReadAccess(u,11));
    assertTrue(AuthFilter.hasReadAccess(u,12));
    assertTrue(AuthFilter.hasReadAccess(u,13));
    assertFalse(AuthFilter.hasReadAccess(u,20));
  }

}