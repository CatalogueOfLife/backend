package life.catalogue.dw.auth;

import life.catalogue.dw.auth.gbif.GBIFAuthentication;

import java.io.IOException;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;

import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Preemptive Basic Auth filter that accept dynamic credentials
 * per request and can be skipped altogether if no credentials are passed
 * - sth impossible with the existing jersey HttpAuthenticationFeature implementation.
 *
 * Only adds an Authentication header if not yet existing.
 */
public class BasicAuthClientFilter implements ClientRequestFilter {
  private static final Logger LOG = LoggerFactory.getLogger(BasicAuthClientFilter.class);
  
  public static class Credentials {
    public final String username;
    public final String password;
  
    public Credentials(String username, String password) {
      this.username = username;
      this.password = password;
    }
  }
  
  @Override
  public void filter(ClientRequestContext request) throws IOException {
    if (request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
      return;
    }
    Credentials creds = extractCredentials(request);
    if (creds != null) {
      LOG.debug("Adding BasicAuth headers for {}", creds.username);
      request.getHeaders().add(HttpHeaders.AUTHORIZATION,
          GBIFAuthentication.basicAuthHeader(creds.username, creds.password)
      );
    }
  }
  
  private Credentials extractCredentials(ClientRequestContext request) {
    String username = (String) request.getProperty(HttpAuthenticationFeature.HTTP_AUTHENTICATION_BASIC_USERNAME);
    String password = (String) request.getProperty(HttpAuthenticationFeature.HTTP_AUTHENTICATION_BASIC_PASSWORD);
    
    return username != null && password != null ? new Credentials(username, password) : null;
  }
}
