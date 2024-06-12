package life.catalogue.dw.auth.gbif;

import life.catalogue.api.model.User;
import life.catalogue.api.vocab.Country;
import life.catalogue.common.io.Resources;
import life.catalogue.common.util.YamlUtils;

import java.io.IOException;

import org.apache.hc.core5.http.impl.client.HttpClients;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class GBIFAuthenticationTest {
  final GBIFAuthentication gbif;
  
  public GBIFAuthenticationTest() throws IOException {
    GBIFAuthenticationFactory factory = YamlUtils.read(GBIFAuthenticationFactory.class, "/gbifAuth.yaml");
    gbif = new GBIFAuthentication(factory);
    gbif.setClient(HttpClients.createDefault());
  }
  
  @Test
  public void basicHeader() {
    // test some non ASCII passwords
    Assert.assertEquals("Basic TGVtbXk6TcO2dMO2cmhlYWQ=", gbif.basicAuthHeader("Lemmy", "Mötörhead"));
  }
  
  @Test
  public void fromJson() throws IOException {
    User u = gbif.fromJson(Resources.stream("gbif-user.json"));
    Assert.assertEquals("manga@mailinator.com", u.getEmail());
    Assert.assertEquals("Mänga", u.getLastname());
    Assert.assertEquals("0000-1234-5678-0011", u.getOrcid());
    Assert.assertEquals(Country.JAPAN, u.getCountry());
  }
  
  @Test
  @Ignore("GBIF service needs to be mocked - this uses live services")
  public void authenticateGBIF() {
    Assert.assertEquals("markus", gbif.authenticateGBIF("markus", "xxx"));
    Assert.assertEquals("colplus", gbif.authenticateGBIF("colplus", "xxx"));
  }
  
  @Test
  @Ignore("GBIF service needs to be mocked - this uses live services")
  public void getUser() {
    User u = gbif.getFullGbifUser("colplus");
    Assert.assertNotNull(u);
  }
}