package life.catalogue.resources.dataset;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.GbifRegistration;
import life.catalogue.api.model.User;
import life.catalogue.gbifsync.GbifRegistrar;

import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import jakarta.ws.rs.ForbiddenException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * The request checks of registering a dataset in GBIF. Write access to the dataset itself is enforced by the
 * AuthFilter through the dataset URL and the roles allowed.
 */
public class DatasetGbifResourceTest {
  static final int KEY = 1000;
  final UUID org = UUID.randomUUID();
  GbifRegistrar registrar;
  DatasetGbifResource resource;

  @Before
  public void init() {
    registrar = mock(GbifRegistrar.class);
    when(registrar.register(anyInt(), any(), anyInt())).thenAnswer(inv ->
      new GbifRegistration(inv.getArgument(0), UUID.randomUUID(), inv.getArgument(2))
    );
    resource = new DatasetGbifResource(registrar);
  }

  private static User user(int key) {
    User u = new User();
    u.setKey(key);
    u.addRole(User.Role.EDITOR);
    return u;
  }

  private DatasetGbifResource.RegistrationRequest req(UUID publisher, boolean accept) {
    var req = new DatasetGbifResource.RegistrationRequest();
    req.publisherKey = publisher;
    req.acceptTerms = accept;
    return req;
  }

  @Test
  public void register() {
    User u = user(101);
    // no organisation publisher role for the organisation
    assertThrows(ForbiddenException.class, () -> resource.register(KEY, req(org, true), u));

    u.getOrgPublisher().add(org);
    assertThrows(IllegalArgumentException.class, () -> resource.register(KEY, req(org, false), u));
    assertThrows(IllegalArgumentException.class, () -> resource.register(KEY, req(null, true), u));
    assertThrows(IllegalArgumentException.class, () -> resource.register(KEY, null, u));
    // another organisation
    assertThrows(ForbiddenException.class, () -> resource.register(KEY, req(UUID.randomUUID(), true), u));
    verifyNoInteractions(registrar);

    var reg = resource.register(KEY, req(org, true), u);
    assertEquals(KEY, (int) reg.getDatasetKey());
    assertEquals(101, (int) reg.getCreatedBy());
    verify(registrar).register(KEY, org, 101);

    // the organisation editor role does not allow publishing
    User editor = user(102);
    editor.getOrgEditor().add(org);
    assertThrows(ForbiddenException.class, () -> resource.register(KEY, req(org, true), editor));

    // admins publish for any organisation
    User admin = user(103);
    admin.addRole(User.Role.ADMIN);
    resource.register(KEY, req(org, true), admin);
    verify(registrar).register(KEY, org, 103);
  }

  @Test
  public void notRegistered() {
    assertThrows(NotFoundException.class, () -> resource.status(KEY));
  }
}
