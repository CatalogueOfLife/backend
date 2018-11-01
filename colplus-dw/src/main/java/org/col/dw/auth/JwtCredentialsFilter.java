package org.col.dw.auth;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.HttpHeaders;

import io.dropwizard.auth.AuthFilter;
import io.dropwizard.auth.Authenticator;
import io.jsonwebtoken.JwtException;
import org.col.api.model.ColUser;

/**
 * Authenticates an CoL user from an encoded Bearer JWT token.
 * See https://tools.ietf.org/html/rfc6750
 * and https://jwt.io/introduction/
 */
public class JwtCredentialsFilter extends AuthFilter<String, ColUser> {
  
  private static final String SCHEME = "JWT";
  
  private static final Pattern BEARER_PATTERN = Pattern.compile("Bearer\\s+(.+)\\s*$");
  
  /**
   * Tries to read the Bearer token from the authorization header if present
   */
  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    final String token = getBearerToken(requestContext.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
    try {
      if (!authenticate(requestContext, token, SCHEME)) {
        throw new WebApplicationException(unauthorizedHandler.buildResponse(prefix, realm));
      }
    } catch (JwtException e) {
      throw new WebApplicationException(ColUnauthorizedHandler.buildResponse(prefix, realm, e.getMessage()));
    }
  }
  
  /**
   * Parses an `Authorization` header content which contains a Bearer JWT token
   * in the form of
   * `Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...`
   *
   * @param header the value of the `Authorization` header
   * @return the BASE64 encoded Bearer token
   */
  @Nullable
  private String getBearerToken(String header) {
    if (header != null) {
      Matcher m = BEARER_PATTERN.matcher(header);
      if (m.find()) {
        return m.group(1);
      }
    }
    return null;
  }
  
  /**
   * Builder for {@link JwtCredentialsFilter}.
   * <p>An {@link Authenticator} must be provided during the building process.</p>
   */
  public static class Builder extends AuthFilterBuilder<String, ColUser, JwtCredentialsFilter> {
    @Override
    protected JwtCredentialsFilter newInstance() {
      return new JwtCredentialsFilter();
    }
  }
}