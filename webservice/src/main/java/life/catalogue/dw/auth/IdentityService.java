package life.catalogue.dw.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.annotations.VisibleForTesting;
import life.catalogue.api.model.User;
import life.catalogue.db.mapper.UserMapper;
import life.catalogue.dw.auth.gbif.GBIFAuthentication;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Identity service that delegates authentication to a pluggable provider
 * It keeps a local copy of users and therefore needs access to Postgres.
 * <p>
 * A SqlSessionFactory and an HttpClient MUST be set before the service is used.
 */
public class IdentityService {
  private static final Logger LOG = LoggerFactory.getLogger(IdentityService.class);
  
  private SqlSessionFactory sqlSessionFactory;
  private final AuthenticationProvider authProvider;
  private final ConcurrentHashMap<String, User> cache; // by username
  private final Cache<String, String> passwords = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(60, TimeUnit.MINUTES)
    .build();

  IdentityService(AuthenticationProvider authProvider) {
    this.authProvider = authProvider;
    this.cache = new ConcurrentHashMap<>();
  }
  
  /**
   * Wires up the mybatis sqlfactory to be used.
   */
  void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
    this.sqlSessionFactory = sqlSessionFactory;
  }
  
  void setClient(CloseableHttpClient http) {
    authProvider.setClient(http);
    // finally we can test the GBIF Auth settings with a well known user
    if (authProvider instanceof GBIFAuthentication) {
      ((GBIFAuthentication)authProvider).verifyGbifAuth();
    }
  }
  
  User get(String username) {
    if (cache.containsKey(username)) {
      return cache.get(username);
    }
    // try to load from DB - if its not there the user has never logged in before and sth is wrong
    try (SqlSession session = sqlSessionFactory.openSession()) {
      User user = session.getMapper(UserMapper.class).getByUsername(username);
      if (user == null) {
        throw new IllegalArgumentException("User " + username + " does not exist");
      }
      return cache(user);
    }
  }

  /**
   * Creates or updates users as they exit in the cache currently.
   * This is needed for tests when the same server runs across several tests which can wipe the database
   * and remove users from the db, but which are still cached.
   * Dont use this for non testing code!
   */
  @VisibleForTesting
  public void flushCachedUsers() {
    try (SqlSession session = sqlSessionFactory.openSession(true)) {
      UserMapper um = session.getMapper(UserMapper.class);
      for (User u : cache.values()) {
        User u2 = um.get(u.getKey());
        if (u2 == null) {
          um.create(u);
        } else {
          um.update(u);
        }
      }
    }
  }

  private User cache(User user) {
    cache.put(user.getUsername(), user);
    return user;
  }

  void invalidate(String username) {
    cache.remove(username);
    passwords.invalidate(username);
  }

  Optional<User> authenticate(String username, String password) {
    Optional<User> optUser;
    String cachedPwd = passwords.getIfPresent(username);
    if (cachedPwd == null || !cachedPwd.equals(password)) {
      // no password cached or cached a different one than provided - do a real authentication and invalidate cached password
      passwords.invalidate(username);
      optUser = authProvider.authenticate(username, password);
      if (optUser.isPresent()) {
        User user = optUser.get();
        user.setLastLogin(LocalDateTime.now());

        // insert/update user in postgres with updated login date
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
          UserMapper mapper = session.getMapper(UserMapper.class);
          // try to find existing user in Col db, otherwise create new one otherwise
          User existing = mapper.getByUsername(user.getUsername());
          if (existing != null) {
            LOG.info("Update CoL user {} {} with latest GBIF information", user.getUsername(), user.getKey());
            user.copyNonGbifData(existing);
            mapper.update(existing);
          } else {
            LOG.info("Creating new CoL user {} {}", user.getUsername(), user.getKey());
            mapper.create(user);
            user.setCreated(LocalDateTime.now());
          }
        } catch (RuntimeException e) {
          LOG.error("IdentityService error", e);
        }
        cache(user);
        passwords.put(username, password);

      } else {
        LOG.debug("Authentication failed for user {}", username);
      }
    } else {
      optUser = Optional.of(get(username));
    }
    return optUser;
  }

}
