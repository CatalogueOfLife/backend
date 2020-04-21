package life.catalogue.dw.jersey.filter;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

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
