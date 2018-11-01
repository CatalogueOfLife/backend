package org.col.dw.auth;

import org.apache.http.impl.client.HttpClients;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class IdentityServiceTest {
  
  final IdentityService ids;
  
  public IdentityServiceTest() {
    AuthConfiguration cfg = new AuthConfiguration();
    cfg.gbifSecret = "none";
    ids = new IdentityService(cfg);
    ids.setClient(HttpClients.createDefault());
  }
  
  @Test
  public void basicHeader() {
    // test some non ASCII passwords
    assertEquals("Basic TGVtbXk6TfZ09nJoZWFk", ids.basicAuthHeader("Lemmy", "Mötörhead"));
  }
  
  @Test
  @Ignore("GBIF service needs to be mocked - this uses live services")
  public void authenticateGBIF() {
    assertNotNull(ids.authenticateGBIF("markus", "markus"));
    assertNotNull(ids.authenticateGBIF("manga", "12345678"));
  }
  
  @Test
  @Ignore("GBIF service needs to be mocked - this uses live services")
  public void getUser() {
    assertNotNull(ids.getFullGbifUser("manga"));
  }
}