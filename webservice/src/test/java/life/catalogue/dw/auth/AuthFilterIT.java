package life.catalogue.dw.auth;

import life.catalogue.api.model.User;

import life.catalogue.db.PgSetupRule;

import life.catalogue.db.TestDataRule;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.net.URI;

import static life.catalogue.api.model.User.Role.ADMIN;
import static life.catalogue.api.model.User.Role.EDITOR;
import static org.junit.Assert.*;

public class AuthFilterIT {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.datasetMix();

  @Test
  public void isAuthorized() {
    User u = new User();
    assertFalse(AuthFilter.isAuthorized(u,10));
    assertFalse(AuthFilter.isAuthorized(u,11));
    assertFalse(AuthFilter.isAuthorized(u,12));
    assertFalse(AuthFilter.isAuthorized(u,13));
    assertFalse(AuthFilter.isAuthorized(u,20));

    u.addDataset(1); // also adds editor role
    assertFalse(AuthFilter.isAuthorized(u,10));
    assertFalse(AuthFilter.isAuthorized(u,11));
    assertFalse(AuthFilter.isAuthorized(u,12));
    assertFalse(AuthFilter.isAuthorized(u,13));
    assertFalse(AuthFilter.isAuthorized(u,20));

    u.addRole(ADMIN);
    assertTrue(AuthFilter.isAuthorized(u,10));
    assertTrue(AuthFilter.isAuthorized(u,11));
    assertTrue(AuthFilter.isAuthorized(u,12));
    assertTrue(AuthFilter.isAuthorized(u,13));
    assertTrue(AuthFilter.isAuthorized(u,20));

    u.removeRole(ADMIN);
    u.addDataset(20);
    assertFalse(AuthFilter.isAuthorized(u,10));
    assertFalse(AuthFilter.isAuthorized(u,11));
    assertFalse(AuthFilter.isAuthorized(u,12));
    assertFalse(AuthFilter.isAuthorized(u,13));
    assertTrue(AuthFilter.isAuthorized(u,20));

    u.addDataset(12); // releases should not be added to permissions, but we make sure here it has no effect
    assertFalse(AuthFilter.isAuthorized(u,10));
    assertFalse(AuthFilter.isAuthorized(u,11));
    assertFalse(AuthFilter.isAuthorized(u,12));
    assertFalse(AuthFilter.isAuthorized(u,13));
    assertTrue(AuthFilter.isAuthorized(u,20));

    u.addDataset(10); // projects give access to all releases
    u.removeDataset(20);
    assertTrue(AuthFilter.isAuthorized(u,10));
    assertTrue(AuthFilter.isAuthorized(u,11));
    assertTrue(AuthFilter.isAuthorized(u,12));
    assertTrue(AuthFilter.isAuthorized(u,13));
    assertFalse(AuthFilter.isAuthorized(u,20));
  }

}