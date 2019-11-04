package org.col.dw.auth;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.ColUser;
import org.col.db.mapper.UserMapper;
import org.col.dw.auth.gbif.GBIFAuthentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Identity service that delegates authentication to a pluggable provider
 * It keeps a local copy of users and therefore needs access to Postgres.
 * <p>
 * A SqlSessionFactory and an HttpClient MUST be set before the service is used.
 */
public class IdentityService {
  private static final Logger LOG = LoggerFactory.getLogger(IdentityService.class);
  
  private SqlSessionFactory sqlSessionFactory;
  private ConcurrentHashMap<String, ColUser> cache;
  private final AuthenticationProvider authProvider;
  
  public IdentityService(AuthenticationProvider authProvider, boolean useCache) {
    this.authProvider = authProvider;
    this.cache = useCache ? new ConcurrentHashMap<>() : null;
  }
  
  /**
   * Wires up the mybatis sqlfactory to be used.
   */
  public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
    this.sqlSessionFactory = sqlSessionFactory;
  }
  
  public void setClient(CloseableHttpClient http) {
    authProvider.setClient(http);
    // finally we can test the GBIF Auth settings with a well known user
    if (authProvider instanceof GBIFAuthentication) {
      ((GBIFAuthentication)authProvider).verifyGbifAuth();
    }
  }
  
  public ColUser get(String username) {
    if (cache != null && cache.containsKey(username)) {
      return cache.get(username);
    }
    // try to load from DB - if its not there the user has never logged in before and sth is wrong
    try (SqlSession session = sqlSessionFactory.openSession()) {
      ColUser user = session.getMapper(UserMapper.class).getByUsername(username);
      if (user == null) {
        throw new IllegalArgumentException("ColUser " + username + " does not exist");
      }
      return cache(user);
    }
  }
  
  private ColUser cache(ColUser user) {
    if (cache != null) {
      cache.put(user.getUsername(), user);
    }
    return user;
  }
  
  public Optional<ColUser> authenticate(String username, String password) {
    Optional<ColUser> optUser = authProvider.authenticate(username, password);
    if (optUser.isPresent()) {
      ColUser user = optUser.get();
      user.getRoles().add(ColUser.Role.USER);
      user.setLastLogin(LocalDateTime.now());
  
      // insert/update coluser in postgres with updated login date
      try (SqlSession session = sqlSessionFactory.openSession(true)) {
        UserMapper mapper = session.getMapper(UserMapper.class);
        // try to find existing user in Col db, otherwise create new one otherwise
        ColUser existing = mapper.getByUsername(user.getUsername());
        if (existing != null) {
          user.setKey(existing.getKey());
          mapper.update(user);
        } else {
          LOG.info("Creating new CoL user {} {}", user.getUsername(), user.getKey());
          mapper.create(user);
          user.setCreated(LocalDateTime.now());
        }
      } catch (RuntimeException e) {
        LOG.error("IdentityService error", e);
      }
      cache(user);
      
    } else {
      LOG.debug("Authentication failed for user {}", username);
    }
    return optUser;
  }

}
