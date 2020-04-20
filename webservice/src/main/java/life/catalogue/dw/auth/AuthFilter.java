package life.catalogue.dw.auth;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Priority;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.Priorities;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.*;

import com.google.common.io.BaseEncoding;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import life.catalogue.api.model.User;
import life.catalogue.dw.jersey.exception.JsonExceptionMapperBase;
import org.apache.commons.lang3.StringUtils;

/**
 * Authenticates an CoL user via Basic or from an encoded Bearer JWT token
 * and populates the security context. OPTIONS preflight requests and GET to the API root are excluded.
 *
 * Otherwise if no authentication is given or it failed a 401 will be send.
 *
 * See https://tools.ietf.org/html/rfc6750
 * and https://jwt.io/introduction/
 */
@Priority(Priorities.AUTHENTICATION)
public class AuthFilter implements ContainerRequestFilter {

  private static final String REALM  = "COL";
  private static final String BASIC  = "Basic";
  private static final String BEARER = "Bearer";
  private static final String TOKEN_PARAM = "token";
  private static final String CHALLENGE_FORMAT = "%s realm=\"%s\"";

  private static final Pattern AUTH_PATTERN = Pattern.compile("^(Basic|Bearer)\\s+(.+)$");
  private static final Pattern DATASET_PATTERN = Pattern.compile("/dataset/([0-9]+)");
  private final IdentityService idService;
  private final JwtCodec jwt;
  private final boolean requireSecure;


  public AuthFilter(IdentityService idService, JwtCodec jwt, boolean requireSecure) {
    this.idService = idService;
    this.jwt = jwt;
    this.requireSecure = requireSecure;
  }

  /**
   * Tries to read the Bearer token from the authorization header if present
   */
  @Override
  public void filter(ContainerRequestContext req) throws IOException {
    if (exclude(req)) return;

    Optional<AuthedUser> user = Optional.empty();

    final String auth = req.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (auth != null) {
      Matcher m = AUTH_PATTERN.matcher(auth.trim());
      if (m.find()) {
        if (m.group(1).equals(BASIC)) {
          if (requireSecure && !isSecure(req)) {
            throw unauthorized("Basic authentication requires SSL");
          }
          user = doBasic(m.group(2));
        } else {
          user = doJWT(m.group(2));
        }
      }
    } else if (req.getUriInfo().getQueryParameters().containsKey(TOKEN_PARAM)) {
      String jwt = req.getUriInfo().getQueryParameters().getFirst(TOKEN_PARAM);
      user = doJWT(jwt);
    }

    if (user.isPresent()) {
      setSecurityContext(user.get(), req);
    } else {
      unauthorized();
    }
  }


  protected static class AuthedUser {
    public final String scheme;
    public final User user;

    static AuthedUser basic(User user) {
      return new AuthedUser(BASIC, user);
    }
    static AuthedUser bearer(User user) {
      return new AuthedUser(BEARER, user);
    }
    private AuthedUser(String scheme, User user) {
      this.scheme = scheme;
      this.user = user;
    }
  }

  private static boolean isSecure(ContainerRequestContext req){
    SecurityContext securityContext = req.getSecurityContext();
    return securityContext != null && securityContext.isSecure();
  }

  void setSecurityContext(final AuthedUser user, final ContainerRequestContext req) {
    final boolean secure = isSecure(req);
    req.setSecurityContext(new SecurityContext() {
      @Override
      public Principal getUserPrincipal() {
        return user.user;
      }

      @Override
      public boolean isUserInRole(String role) {
        Integer datasetKey = requestedDataset(req.getUriInfo());
        return user.user.hasRole(role, datasetKey);
      }

      @Override
      public boolean isSecure() {
        return secure;
      }

      @Override
      public String getAuthenticationScheme() {
        return user.scheme;
      }
    });

  }

  static Integer requestedDataset(UriInfo uri){
    return requestedDataset(uri.getAbsolutePath());
  }

  static Integer requestedDataset(URI absoluteUri){
    Matcher m = DATASET_PATTERN.matcher(absoluteUri.toString());
    if (m.find()) {
      return Integer.parseInt(m.group(1));
    }
    return null;
  }

  static WebApplicationException unauthorized(String msg) {
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
  private Optional<AuthedUser> doBasic(String token) {
    try {
      String cred = new String(BaseEncoding.base64().decode(token), StandardCharsets.UTF_8);
      String[] parts = cred.split(":", 2  );
      return idService.authenticate(parts[0], parts[1]).map(AuthedUser::basic);

    } catch (Exception e) {
      throw unauthorized("Basic authentication error: " + e.getMessage());
    }
  }

  /**
   * @param token BASE64 encoded JWT token
   */
  private Optional<AuthedUser> doJWT(String token) {
    try {
      Jws<Claims> jws = jwt.parse(token);
      return Optional.of(jws.getBody().getSubject())
        .map(idService::get)
        .map(AuthedUser::bearer);

    } catch (ExpiredJwtException ex) {
      throw unauthorized("Expired JWT token: " + ex.getClaims().getExpiration());

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

  private static boolean exclude(ContainerRequestContext req){
    return req.getMethod().equals(HttpMethod.OPTIONS)
      || StringUtils.isBlank(req.getUriInfo().getPath());
  }

  private static void unauthorized() {
    throw unauthorized("Failed to authenticate via Basic or Bearer JWT");
  }

}