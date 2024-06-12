package life.catalogue.dw.jersey.filter;

import java.io.IOException;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Filter that adds WWW-Authenticate headers in case we had an UNAUTHORIZED response.
 */
@Provider
public class NotAuthResponseFilter implements ContainerResponseFilter {

  private static final String REALM  = "COL";
  private static final String CHALLENGE_FORMAT = "%s realm=\"%s\"";

  @Override
  public void filter(ContainerRequestContext request, ContainerResponseContext response) throws IOException {
    if (response.getStatus() == Response.Status.UNAUTHORIZED.getStatusCode()) {
      response.getHeaders().add(HttpHeaders.WWW_AUTHENTICATE, String.format(CHALLENGE_FORMAT, "Basic", REALM));
      // see https://tools.ietf.org/html/rfc6750#page-7
      response.getHeaders().add(HttpHeaders.WWW_AUTHENTICATE, String.format(CHALLENGE_FORMAT, "Bearer token_type=\"JWT\"", REALM));
    }
  }
}
