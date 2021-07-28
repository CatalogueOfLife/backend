package life.catalogue.dw.auth;

import life.catalogue.api.model.User;

import org.junit.Test;

import java.net.URI;

import static life.catalogue.api.model.User.Role.ADMIN;
import static life.catalogue.api.model.User.Role.EDITOR;
import static org.junit.Assert.*;

public class AuthFilterTest {

  @Test
  public void requestedDataset() {
    assertNull(AuthFilter.requestedDataset(URI.create("")));
    assertNull(AuthFilter.requestedDataset(URI.create("/")));
    assertNull(AuthFilter.requestedDataset(URI.create("/dataset")));
    assertNull(AuthFilter.requestedDataset(URI.create("dataset")));
    assertNull(AuthFilter.requestedDataset(URI.create("/dataset/me")));
    assertNull(AuthFilter.requestedDataset(URI.create("/datasets/1234")));
    assertNull(AuthFilter.requestedDataset(URI.create("dataset/1234")));
    assertNull(AuthFilter.requestedDataset(URI.create("/dataset1234")));

    assertEquals((Integer) 1234, AuthFilter.requestedDataset(URI.create("/dataset/1234")));
    assertEquals((Integer) 1234, AuthFilter.requestedDataset(URI.create("/dataset/1234")));
    assertEquals((Integer) 1234, AuthFilter.requestedDataset(URI.create("/dataset/1234/")));
    assertEquals((Integer) 1234, AuthFilter.requestedDataset(URI.create("/dataset/1234/name")));
  }

  @Test
  public void isAuthorized() {
    User u = new User();
    assertFalse(AuthFilter.isAuthorized(u,1));

    u.addDataset(1);
    assertTrue(AuthFilter.isAuthorized(u,1));

    u.addRole(ADMIN);
    assertTrue(AuthFilter.isAuthorized(u,1));

    u.removeRole(ADMIN);
    assertTrue(AuthFilter.isAuthorized(u,1));

    u.addRole(EDITOR);
    assertTrue(AuthFilter.isAuthorized(u,1));
    assertFalse(AuthFilter.isAuthorized(u,2));

    u.addDataset(2);
    assertTrue(AuthFilter.isAuthorized(u,2));
  }

}