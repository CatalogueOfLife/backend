package org.col.dw.auth;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.ColUser;
import org.col.db.mapper.UserMapper;
import org.col.dw.auth.gbif.GbifTrustedAuth;
import org.col.dw.auth.gbif.HttpGbifAuthFilter;
import org.gbif.api.vocabulary.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Identity service that delegates authentication to the BASIC scheme using the
 * GBIF id service via http.
 * It keeps a local copy of users and therefore needs access to Postgres.
 *
 * A SqlSessionFactory and an HttpClient MUST be set before the service is used.
 */
public class IdentityService implements Authenticator<BasicCredentials, ColUser> {
  private static final Logger LOG = LoggerFactory.getLogger(IdentityService.class);
  private static final String SETTING_COUNTRY = "country";
  private static final String SYS_SETTING_ORCID = "auth.orcid.id";
  private static final AuthScope gbifScope = new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, AuthScope.ANY_REALM, AuthScope.ANY_SCHEME);
  
  private final AuthConfiguration cfg;
  private SqlSessionFactory sqlSessionFactory;
  private ConcurrentHashMap<Integer, ColUser> cache;
  private final URI loginUri;
  private final URI userUri;
  private CloseableHttpClient http;
  
  public IdentityService(AuthConfiguration cfg) {
    this.cfg = cfg;
    try {
      loginUri = new URI(cfg.gbifApi).resolve("user/login");
      userUri = new URI(cfg.gbifApi).resolve("admin/user/");
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
    LOG.info("Accessing GBIF user accounts at {}", cfg.gbifApi);
    
    //TODO: replace with Chronicle Map and only cache the main webservice, not admin!
    this.cache = new ConcurrentHashMap<>();
    // dummy user until we truly connect to GBIF
    ColUser iggy = new ColUser();
    iggy.setKey(1969);
    iggy.setUsername("iggy");
    iggy.setFirstname("James");
    iggy.setLastname("Osterberg");
    iggy.setEmail("iggy@mailinator.com");
    iggy.setOrcid("0000-0000-0000-0666");
    iggy.setRoles(Arrays.stream(ColUser.Role.values()).collect(Collectors.toSet()));
    cache.put(iggy.getKey(), iggy);
  }
  
  /**
   * Wires up the mybatis sqlfactory to be used.
   */
  public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
    this.sqlSessionFactory = sqlSessionFactory;
  }
  
  public void setClient(CloseableHttpClient http) {
    this.http = http;
  }

  public ColUser get(Integer key) {
    if (cache.containsKey(key)) {
      return cache.get(key);
    }
    // try to load from DB - if its not there the user has never logged in before and sth is wrong
    try (SqlSession session = sqlSessionFactory.openSession()) {
      ColUser user = session.getMapper(UserMapper.class).get(key);
      if (user == null) {
        throw new IllegalArgumentException("ColUser " + key + " does not exist");
      }
      return cache(user);
    }
  }

  private ColUser cache(ColUser user) {
    cache.put(user.getKey(), user);
    return user;
  }
  
  @VisibleForTesting
  Optional<ColUser> authenticate(String username, String password) {
    // TODO: remove temp hack
    if ("iggy".equalsIgnoreCase(username) && "NoFun".equals(password)) {
      return Optional.of(cache.get(1969));
    }
    
    ColUser user = authenticateGBIF(username, password);
    if (user != null) {
      if (false) {
        // TODO: GBIF authentication does not provide us with the full user, we need to look it up again
        user = getFullGbifUser(username);
      }
      user.getRoles().add(ColUser.Role.USER);
      user.setLastLogin(LocalDateTime.now());
      // insert/update coluser in postgres with updated login date
      try (SqlSession session = sqlSessionFactory.openSession(true)) {
        UserMapper mapper = session.getMapper(UserMapper.class);
        // try to update user, create new one otherwise
        if (mapper.update(user) < 1) {
          LOG.info("Creating new CoL user {} {}", user.getUsername(), user.getKey());
          mapper.create(user);
          user.setCreated(LocalDateTime.now());
        }
      }
      return Optional.of(cache(user));
    }
    return Optional.empty();
  }
  
  /**
   * Checks if Basic Auth against GBIF API is working
   */
  @VisibleForTesting
  ColUser authenticateGBIF(String username, String password) {
  
    CredentialsProvider credsProvider = new BasicCredentialsProvider();
    credsProvider.setCredentials(gbifScope, new UsernamePasswordCredentials(username, password));

    HttpClientContext context = HttpClientContext.create();
    context.setCredentialsProvider(credsProvider);

    HttpGet get = new HttpGet(loginUri);
    try (CloseableHttpResponse resp = http.execute(get, context)){
      LOG.debug("GBIF authentication response for {}: {}", username, resp);
      if (resp.getStatusLine().getStatusCode() == 200) {
        return fromJson("");
      }
    
    } catch (Exception e) {
      LOG.error("GBIF BasicAuth error", e);
    }
    return null;
  }
  
  @VisibleForTesting
  ColUser getFullGbifUser(String username) {
    try {
      new HttpGbifAuthFilter(new GbifTrustedAuth(cfg));
      HttpGet get = new HttpGet(userUri.resolve(username));
      CloseableHttpResponse resp = http.execute(get);

    } catch (Exception e) {
      LOG.info("Failed to retrieve GBIF user {}", username, e);
    }
    return null;
  }
  
  @Override
  public Optional<ColUser> authenticate(BasicCredentials credentials) throws AuthenticationException {
    return authenticate(credentials.getUsername(), credentials.getPassword());
  }
  
  private ColUser fromJson(String json) {
    ColUser user = new ColUser();
    return user;
  }
  
  private static ColUser.Role fromGbifRole(UserRole gbif) {
    switch (gbif) {
      case USER: return ColUser.Role.USER;
      case REGISTRY_ADMIN: return ColUser.Role.ADMIN;
      case REGISTRY_EDITOR: return ColUser.Role.EDITOR;
    }
    return null;
  }
}
