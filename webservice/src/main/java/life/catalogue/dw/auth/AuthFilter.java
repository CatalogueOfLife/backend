package life.catalogue.dw.auth;

import com.google.common.io.BaseEncoding;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import life.catalogue.api.model.User;
import life.catalogue.dw.jersey.exception.JsonExceptionMapperBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authenticates an CoL user via Basic or from an encoded Bearer JWT token
 * and populates the security context. OPTIONS preflight requests are excluded.
 *
 * Otherwise if no authentication is given or it failed a 401 will be send.
 *
 * See https://tools.ietf.org/html/rfc6750
 * and https://jwt.io/introduction/
 */
@Priority(Priorities.AUTHENTICATION)
public class AuthFilter implements ContainerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(AuthFilter.class);

  private static final String BASIC  = "Basic";
  private static final String BEARER = "Bearer";
  private static final String TOKEN_PARAM = "token";

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

  @Override
  public void filter(ContainerRequestContext req) throws IOException {
    AuthedUser user = null;

    final String auth = req.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (auth != null) {
      Matcher m = AUTH_PATTERN.matcher(auth.trim());
      if (m.find()) {
        if (m.group(1).equals(BASIC)) {
          if (requireSecure && !isSecure(req)) {
            throw authenticationError("Basic authentication requires SSL");
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

    if (user != null) {
      setSecurityContext(user, req);
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

  static WebApplicationException authenticationError(String msg) {
    Response resp = JsonExceptionMapperBase.jsonErrorResponseBuilder(Response.Status.UNAUTHORIZED, msg).build();
    return new WebApplicationException(resp);
  }

  /**
   * @param token BASE64 encoded Basic credentials
   */
  private AuthedUser doBasic(String token) {
    try {
      String cred = new String(BaseEncoding.base64().decode(token), StandardCharsets.UTF_8);
      String[] parts = cred.split(":", 2  );
      return idService.authenticate(parts[0], parts[1])
        .map(AuthedUser::basic)
        .orElseThrow(() -> {
          throw authenticationError("Basic authentication failed");
        });

    } catch (WebApplicationException e) {
      throw e;

    } catch (Exception e) {
      LOG.info("Basic authentication error", e);
      throw authenticationError("Basic authentication failed");
    }
  }

  /**
   * @param token BASE64 encoded JWT token
   */
  private AuthedUser doJWT(String token) {
    try {
      Jws<Claims> jws = jwt.parse(token);
      return Optional.of(jws.getBody().getSubject())
        .map(idService::get)
        .map(AuthedUser::bearer)
        .orElseThrow(() -> {
          throw authenticationError("Bearer authentication failed");
        });

    } catch (WebApplicationException e) {
      throw e;

    } catch (ExpiredJwtException ex) {
      throw authenticationError("Expired JWT token: " + ex.getClaims().getExpiration());

    } catch (JwtException | IllegalArgumentException ex) {
      StringBuilder sb = new StringBuilder("Invalid JWT token");
      if (ex.getMessage() != null) {
        sb.append(": ").append(ex.getMessage());
      }
      throw authenticationError(sb.toString());

    } catch (Exception e) {
      LOG.info("JWT authentication error", e);
      throw authenticationError("JWT authentication failed");
    }
  }

}