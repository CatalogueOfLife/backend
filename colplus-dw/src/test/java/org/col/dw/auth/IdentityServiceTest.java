package org.col.dw.auth;

import java.io.IOException;

import org.apache.http.impl.client.HttpClients;
import org.col.api.model.ColUser;
import org.col.api.vocab.Country;
import org.col.common.io.Resources;
import org.col.common.util.YamlUtils;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

public class IdentityServiceTest {
  
  final IdentityService ids;
  
  public IdentityServiceTest() throws IOException {
    AuthConfiguration cfg = YamlUtils.read(AuthConfiguration.class, "/auth.yaml");
    ids = new IdentityService(cfg);
    ids.setClient(HttpClients.createDefault());
  }
  
  @Test
  public void basicHeader() {
    // test some non ASCII passwords
    assertEquals("Basic TGVtbXk6TfZ09nJoZWFk", ids.basicAuthHeader("Lemmy", "Mötörhead"));
  }
  
  @Test
  public void fromJson() throws IOException {
    ColUser u = ids.fromJson(Resources.stream("gbif-user.json"));
    assertEquals("manga@mailinator.com", u.getEmail());
    assertEquals("Mänga", u.getLastname());
    assertEquals("0000-1234-5678-0011", u.getOrcid());
    assertEquals(Country.JAPAN, u.getCountry());
  }
  
  @Test
  @Ignore("GBIF service needs to be mocked - this uses live services")
  public void authenticateGBIF() {
    assertTrue(ids.authenticateGBIF("manga", ""));
    assertTrue(ids.authenticateGBIF("markus", ""));
  }
  
  @Test
  @Ignore("GBIF service needs to be mocked - this uses live services")
  public void getUser() {
    ColUser u = ids.getFullGbifUser("manga");
    assertNotNull(u);
  }
}