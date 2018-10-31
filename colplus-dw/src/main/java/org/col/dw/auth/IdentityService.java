package org.col.dw.auth;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.ColUser;
import org.col.api.vocab.Country;
import org.col.db.mapper.UserMapper;
import org.gbif.api.model.common.GbifUser;
import org.gbif.api.vocabulary.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Identity service that delegates authentication to the BASIC scheme using the
 * GBIF id service.
 * It keeps a local copy of users and therefore needs access to Postgres.
 *
 * A SqlSessionFactory MUST be set before the service is used.
 */
public class IdentityService implements Authenticator<BasicCredentials, ColUser> {
  private static final Logger LOG = LoggerFactory.getLogger(IdentityService.class);
  private static final String SETTING_COUNTRY = "country";
  private static final String SYS_SETTING_ORCID = "auth.orcid.id";
  
  private final AuthConfiguration cfg;
  private SqlSessionFactory sqlSessionFactory;
  private ConcurrentHashMap<Integer, ColUser> cache;
  
  public IdentityService(AuthConfiguration cfg) {
    this.cfg = cfg;
    //TODO: replace with Chronicle Map
    this.cache = new ConcurrentHashMap<>();
    // dummy user until we truely connect to GBIF
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
  
  public ColUser get(Integer key) {
    if (cache.contains(key)) {
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
  
  private Optional<ColUser> authenticate(String username, String password) {
    // TODO: remove temp hack
    if ("iggy".equalsIgnoreCase(username) && "NoFun".equals(password)) {
      return Optional.of(cache.get(1969));
    }
    
    if (authenticateGBIF(username, password)) {
      // GBIF authentication does not provide us with the full user, we need to look it up again
      ColUser user = getFullGbifUser(username);
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
  
  private boolean authenticateGBIF(String username, String password) {
    // TODO: authenticate against GBIF API
    return false;
  }

  private ColUser getFullGbifUser(String username) {
    // TODO: get full user from GBIF API via trusted app auth
    return new ColUser();
  }
  
  @Override
  public Optional<ColUser> authenticate(BasicCredentials credentials) throws AuthenticationException {
    return authenticate(credentials.getUsername(), credentials.getPassword());
  }
  
  static ColUser fromGbif(GbifUser gbif) {
    ColUser user = new ColUser();
    user.setUsername(gbif.getUserName());
    user.setFirstname(gbif.getFirstName());
    user.setLastname(gbif.getLastName());
    user.setRoles(gbif.getRoles().stream()
        .map(IdentityService::fromGbifRole)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet()));
    if (gbif.getLastLogin() != null) {
      user.setLastLogin(LocalDateTime.from(gbif.getLastLogin().toInstant()));
    }
    // settings
    if (gbif.getSettings().containsKey(SETTING_COUNTRY)) {
      user.setCountry(Country.fromIsoCode(gbif.getSettings().get(SETTING_COUNTRY)).orElse(null));
    }
    if (gbif.getSystemSettings().containsKey(SYS_SETTING_ORCID)) {
      user.setOrcid(gbif.getSystemSettings().get(SYS_SETTING_ORCID));
    }
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
