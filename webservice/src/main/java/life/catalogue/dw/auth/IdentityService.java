package life.catalogue.dw.auth;

import life.catalogue.api.model.User;
import life.catalogue.db.mapper.UserMapper;
import life.catalogue.dw.auth.gbif.GBIFAuthentication;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.annotations.VisibleForTesting;

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
  private final ConcurrentHashMap<Integer, String> key2username; // never expires, immutable and small footprint
  private final ConcurrentHashMap<String, User> cache; // by username
  private final Cache<String, String> passwords = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(60, TimeUnit.MINUTES)
    .build();

  IdentityService(AuthenticationProvider authProvider) {
    this.authProvider = authProvider;
    this.cache = new ConcurrentHashMap<>();
    this.key2username = new ConcurrentHashMap<>();
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

  public User get(int userKey) {
    if (key2username.containsKey(userKey)) {
      return get(key2username.get(userKey));
    }
    // try to load from DB - if its not there the user has never logged in before and sth is wrong
    try (SqlSession session = sqlSessionFactory.openSession()) {
      User user = session.getMapper(UserMapper.class).get(userKey);
      if (user == null) {
        throw new IllegalArgumentException("User " + userKey + " does not exist");
      }
      return cache(user);
    }
  }

  User get(String username) {
    if (cache.containsKey(username)) {
      return cache.get(username);
    }
    // try to load from DB - if it's not there the user has never logged in before and sth is wrong
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
  public void persistCachedUsers() {
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
    key2username.put(user.getKey(), user.getUsername());
    return user;
  }

  void invalidate(String username) {
    cache.remove(username);
    passwords.invalidate(username);
  }

  Optional<User> authenticate(final String username, final String password) {
    Optional<User> optUser;
    String cachedPwd = passwords.getIfPresent(username);
    if (cachedPwd == null || !cachedPwd.equals(password)) {
      // no password cached or cached a different one than provided - do a real authentication and invalidate cached password
      passwords.invalidate(username);
      // this loads the user from gbif or text files, not the CLB DB!
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
            LOG.debug("Update CoL user {} [{}] with latest GBIF information", existing.getUsername(), existing.getKey());
            user.copyNonGbifData(existing);
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
        passwords.put(username, password);

      } else {
        LOG.debug("Authentication failed for user {}", username);
      }
    } else {
      optUser = Optional.of(get(username));
    }
    return optUser;
  }

  public int flush() {
    int num = cache.size();
    cache.clear();
    return num;
  }

  /**
   * Invalidates all users which are in the SCL lists of the given dataset
   * @param datasetKey
   */
  public void invalidateByDataset(int datasetKey) {
    Set<String> usernames = new HashSet<>();
    try (SqlSession session = sqlSessionFactory.openSession(true)) {
      UserMapper um = session.getMapper(UserMapper.class);
      for (var u : um.datasetEditors(datasetKey)) {
        usernames.add(u.getUsername());
      }
      for (var u : um.datasetReviewer(datasetKey)) {
        usernames.add(u.getUsername());
      }
    }
    for (String u : usernames) {
      invalidate(u);
    }
  }
}
