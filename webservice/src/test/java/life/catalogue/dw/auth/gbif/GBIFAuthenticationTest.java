package life.catalogue.dw.auth.gbif;

import life.catalogue.api.model.User;
import life.catalogue.api.vocab.area.Country;
import life.catalogue.common.io.Resources;
import life.catalogue.common.util.YamlUtils;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.jupiter.api.Disabled;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
  
  /**
   * A registry that never answered says nothing about our credentials, so startup must go on.
   * See the GBIF test registry outage on 2026-08-26 which took the dev servers down.
   */
  @Test
  public void verifyKeepsGoingWhenGbifIsUnavailable() {
    gbif.verify(GBIFAuthentication.UserLookup.noResponse(new SocketTimeoutException("Read timed out")));
    gbif.verify(GBIFAuthentication.UserLookup.answered(HttpStatus.SC_SERVICE_UNAVAILABLE));
    gbif.verify(GBIFAuthentication.UserLookup.answered(HttpStatus.SC_NOT_FOUND));
  }

  /**
   * A refused request means our appkey or secret is wrong - that must not go live.
   */
  @Test
  public void verifyFailsWhenGbifRefusesUs() {
    for (int status : new int[]{HttpStatus.SC_UNAUTHORIZED, HttpStatus.SC_FORBIDDEN}) {
      try {
        gbif.verify(GBIFAuthentication.UserLookup.answered(status));
        Assert.fail("HTTP " + status + " must abort the startup");
      } catch (IllegalStateException e) {
        // expected
      }
    }
  }

  @Test
  public void verifySucceedsWithUser() {
    gbif.verify(GBIFAuthentication.UserLookup.found(new User()));
  }

  /**
   * The lookup itself has to classify the outcome - a timeout and a 403 used to both end up as a plain null.
   */
  @Test
  public void lookupClassifiesTheOutcome() throws Exception {
    var http = mock(CloseableHttpClient.class);
    var gbif = new GBIFAuthentication(YamlUtils.read(GBIFAuthenticationFactory.class, "/gbifAuth.yaml"));
    gbif.setClient(http);

    var resp = mock(CloseableHttpResponse.class);
    when(resp.getCode()).thenReturn(HttpStatus.SC_FORBIDDEN);
    when(http.execute(any(ClassicHttpRequest.class))).thenReturn(resp);
    var refused = gbif.lookupGbifUser("markus");
    Assert.assertNull(refused.user());
    Assert.assertTrue(refused.refused());

    when(http.execute(any(ClassicHttpRequest.class))).thenThrow(new SocketTimeoutException("Read timed out"));
    var unavailable = gbif.lookupGbifUser("markus");
    Assert.assertNull(unavailable.user());
    Assert.assertNull(unavailable.status());
    Assert.assertFalse(unavailable.refused());
  }

  @Test
  @Disabled @Ignore("GBIF service needs to be mocked - this uses live services")
  public void authenticateGBIF() {
    Assert.assertEquals("markus", gbif.authenticateGBIF("markus", "xxx"));
    Assert.assertEquals("colplus", gbif.authenticateGBIF("colplus", "xxx"));
  }
  
  @Test
  @Disabled @Ignore("GBIF service needs to be mocked - this uses live services")
  public void getUser() throws URISyntaxException {
    User u = gbif.getFullGbifUser("colplus");
    Assert.assertNotNull(u);
  }
}