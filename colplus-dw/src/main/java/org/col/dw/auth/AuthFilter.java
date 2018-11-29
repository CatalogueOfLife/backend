package org.col.dw.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import com.google.common.io.BaseEncoding;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import org.col.api.model.ColUser;
import org.col.dw.jersey.exception.JsonExceptionMapperBase;

/**
 * Authenticates an CoL user via Basic or from an encoded Bearer JWT token.
 * See https://tools.ietf.org/html/rfc6750
 * and https://jwt.io/introduction/
 */
@Priority(Priorities.AUTHENTICATION)
public class AuthFilter implements ContainerRequestFilter {
  
  private static final String REALM = "COL";
  private static final String BASIC = "Basic";
  private static final String BEARER = "Bearer";
  private static final String CHALLENGE_FORMAT = "%s realm=\"%s\"";
  
  private static final Pattern AUTH_PATTERN = Pattern.compile("^(Basic|Bearer)\\s+(.+)$");
  private final IdentityService idService;
  private final JwtCodec jwt;
  
  
  public AuthFilter(IdentityService idService, JwtCodec jwt) {
    this.idService = idService;
    this.jwt = jwt;
  }
  
  /**
   * Tries to read the Bearer token from the authorization header if present
   */
  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    final String auth = requestContext.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    String scheme = null;
    Optional<ColUser> user = Optional.empty();
    if (auth != null) {
      Matcher m = AUTH_PATTERN.matcher(auth.trim());
      if (m.find()) {
        if (m.group(1).equals(BASIC)) {
          user = doBasic(m.group(2));
          scheme = BASIC;
        } else {
          user = doJWT(m.group(2));
          scheme = BEARER;
        }
      }
    }
    if (user.isPresent()) {
      setSecurityContext(user.get(), scheme, requestContext);
    } else {
      unauthorized();
    }
  }
  
  private void setSecurityContext(final ColUser user, final String scheme, final ContainerRequestContext requestContext) {
    final SecurityContext securityContext = requestContext.getSecurityContext();
    final boolean secure = securityContext != null && securityContext.isSecure();
    
    requestContext.setSecurityContext(new SecurityContext() {
      @Override
      public Principal getUserPrincipal() {
        return user;
      }
      
      @Override
      public boolean isUserInRole(String role) {
        return user.hasRole(role);
      }
      
      @Override
      public boolean isSecure() {
        return secure;
      }
      
      @Override
      public String getAuthenticationScheme() {
        return scheme;
      }
    });
  }
  
  private static void unauthorized() {
    throw unauthorized("Failed to authenticate via Basic or Bearer JWT");
  }
  
  private static WebApplicationException unauthorized(String msg) {
    Response resp = JsonExceptionMapperBase.jsonErrorResponseBuilder(Response.Status.UNAUTHORIZED, msg)
        .header(HttpHeaders.WWW_AUTHENTICATE, String.format(CHALLENGE_FORMAT, "Basic", REALM))
        // see https://tools.ietf.org/html/rfc6750#page-7
        .header(HttpHeaders.WWW_AUTHENTICATE, String.format(CHALLENGE_FORMAT, "Bearer token_type=\"JWT\"", REALM))
        .build();
    return new WebApplicationException(resp);
  }
  
  /**
   * @param token BASE64 encoded Basic credentials
   */
  private Optional<ColUser> doBasic(String token) {
    try {
      String cred = new String(BaseEncoding.base64().decode(token), StandardCharsets.UTF_8);
      String[] parts = cred.split(":", 2  );
      return idService.authenticate(parts[0], parts[1]);
      
    } catch (Exception e) {
      throw unauthorized("Basic authentication error: " + e.getMessage());
    }
  }
  
  /**
   * @param token BASE64 encoded JWT token
   */
  private Optional<ColUser> doJWT(String token) {
    try {
      Jws<Claims> jws = jwt.parse(token);
      return Optional.of(Integer.valueOf(jws.getBody().getSubject()))
          .map(idService::get);
      
    } catch (JwtException | IllegalArgumentException ex) {
      StringBuilder sb = new StringBuilder("Invalid JWT token");
      if (ex.getMessage() != null) {
        sb.append(": ").append(ex.getMessage());
      }
      throw unauthorized(sb.toString());
      
    } catch (Exception e) {
      throw unauthorized("JWT authentication failed: " + e.getMessage());
    }
  }
  
}