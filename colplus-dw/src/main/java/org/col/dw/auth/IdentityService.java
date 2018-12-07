package org.col.dw.auth;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.ColUser;
import org.col.db.mapper.UserMapper;
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
  private ConcurrentHashMap<Integer, ColUser> cache;
  private final AuthenticationProvider authProvider;
  
  private static ObjectMapper configure(ObjectMapper mapper) {
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    return mapper;
  }
  
  public IdentityService(AuthenticationProvider authProvider) {
    this.authProvider = authProvider;
    this.cache = new ConcurrentHashMap<>();
  }
  
  /**
   * Wires up the mybatis sqlfactory to be used.
   */
  public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
    this.sqlSessionFactory = sqlSessionFactory;
  }
  
  public void setClient(CloseableHttpClient http) {
    authProvider.setClient(http);
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
        ColUser existing = mapper.getByUsername(username);
        if (existing != null) {
          user.setKey(existing.getKey());
          mapper.update(user);
        } else {
          LOG.info("Creating new CoL user {} {}", user.getUsername(), user.getKey());
          mapper.create(user);
          user.setCreated(LocalDateTime.now());
        }
      }
      cache(user);
      
    } else {
      LOG.debug("GBIF authentication failed for user {}", username);
    }
    return optUser;
  }

}
