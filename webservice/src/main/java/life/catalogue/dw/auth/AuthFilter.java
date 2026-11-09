package life.catalogue.dw.auth;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.User;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dw.jersey.exception.JsonExceptionMapperBase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.BaseEncoding;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.PathSegment;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;

/**
 * Authenticates an CoL user via Basic or from an encoded Bearer JWT token
 * and populates the security context. OPTIONS preflight requests are excluded.
 * The Bearer JWT token can alternatively also be given as a URI query parameter called "token".
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

  static final Pattern AUTH_PATTERN = Pattern.compile("^(Basic|Bearer)\\s+(.+)$");
  private static final Pattern DIGITS = Pattern.compile("^[0-9]+$");
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
      if (user.user.isBlockedUser()) {
        throw authorizationError("User " + user.user.getUsername() + " is blocked. Please contact support.");
      }
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
        try {
          User.Role r = User.Role.valueOf(role.trim().toUpperCase());
          // global roles assigned to the user?
          if (user.user.getRoles().contains(r)) {
            return true;
          }
          // the editor and reviewer role is also scoped by datasetKey, see https://github.com/CatalogueOfLife/backend/issues/580
          // Check if the user has permissions. For releases use the project key for evaluation.
          if (r == User.Role.EDITOR) {
            return hasWriteAccess(user.user, datasetKey);
          }
          if (r == User.Role.REVIEWER && datasetKey != null) {
            return hasReadAccess(user.user, datasetKey);
          }

        } catch (IllegalArgumentException e) {
          // swallow, we dont know this role so the user doesnt have it.
        }
        return false;

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

  /**
   * Evaluates if a user has read permissions on a given dataset.
   * Admins have access to all datasets. Editors and reviewers only to the ones explicitly listed in the users properties.
   * Project releases are evaluated by the project only, thus an editor of a project always has access to all releases.
   */
  public static boolean hasReadAccess(User user, int datasetKey){
    try {
      // use the project key to evaluate permissions
      var info = DatasetInfoCache.CACHE.info(datasetKey, true);
      int masterKey = info.keyOrProjectKey();
      return user.isAdmin() || user.isEditor(masterKey) || user.isReviewer(masterKey) || user.isPublisher(info.publisherKey);
    } catch (NotFoundException e) {
      return false;
    }
  }

  /**
   * Evaluates if a user has write permission on a given dataset or can create new datasets, i.e. the datasetKey is null.
   * Admins have access to all datasets. Editors only to the ones explicitly listed in the users property.
   * Project releases are evaluated by the project only, thus an editor of a project always has access to all releases.
   */
  public static boolean hasWriteAccess(User user, Integer datasetKey){
    if (datasetKey == null) {
      // creating a new dataset requires a global editor role or at least one existing editor right on some dataset
      return user.isAdmin() || !user.getEditor().isEmpty();

    } else {
      try {
        // use the project key to evaluate permissions
        var info = DatasetInfoCache.CACHE.info(datasetKey, true);
        int masterKey = info.keyOrProjectKey();
        return user.isAdmin() || user.isEditor(masterKey) || user.isPublisher(info.publisherKey);
      } catch (NotFoundException e) {
      }
    }
    return false;
  }

  /**
   * The dataset key of a /dataset/{key} request, read from the decoded path segments without their matrix parameters,
   * i.e. the very value Jersey hands to the resource as its {key} path parameter.
   * Only plain ASCII digits are accepted as a key. Anything else Jersey would still convert into an integer -
   * a sign, non ASCII digits, surrounding whitespace or an encoded form of those - is rejected with a 400:
   * the resource would see the dataset while the authorization and private filters did not, see gbif/informatics#85 (A3).
   *
   * @return the requested dataset key or null if the path does not address a dataset
   * @throws WebApplicationException with status 400 for a dataset key written in any but the canonical form
   */
  static Integer requestedDataset(UriInfo uri){
    return requestedDataset(uri.getPathSegments(true).stream()
      .map(PathSegment::getPath)
      .toList()
    );
  }

  /**
   * @param path a decoded path, relative to the application root, with optional matrix parameters on any segment
   */
  @VisibleForTesting
  static Integer requestedDataset(String path){
    return requestedDataset(Arrays.stream(path.split("/"))
      .map(seg -> seg.contains(";") ? seg.substring(0, seg.indexOf(';')) : seg)
      .toList()
    );
  }

  private static Integer requestedDataset(List<String> segments){
    // ignore empty segments so a doubled slash cannot hide the key
    var segs = segments.stream().filter(s -> !s.isEmpty()).toList();
    if (segs.size() < 2 || !segs.get(0).equals("dataset")) {
      return null;
    }
    final String key = segs.get(1);
    if (DIGITS.matcher(key).matches()) {
      try {
        return Integer.parseInt(key);
      } catch (NumberFormatException e) {
        throw invalidDatasetKey(); // too large for an int
      }
    }
    // anything else Jersey would convert into an integer must not pass unseen
    try {
      Integer.valueOf(key.strip());
      throw invalidDatasetKey();
    } catch (NumberFormatException e) {
      return null; // not a key at all, e.g. /dataset/keys
    }
  }

  private static WebApplicationException invalidDatasetKey() {
    return error(Response.Status.BAD_REQUEST, "Invalid dataset key. Dataset keys consist of ASCII digits only");
  }

  static WebApplicationException authenticationError(String msg) {
    return error(Response.Status.UNAUTHORIZED, msg);
  }

  static WebApplicationException authorizationError(String msg) {
    return error(Response.Status.FORBIDDEN, msg);
  }

  static WebApplicationException error(Response.Status status, String msg) {
    Response resp = JsonExceptionMapperBase.jsonErrorResponse(status, msg);
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
        .map(idService::touch)
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