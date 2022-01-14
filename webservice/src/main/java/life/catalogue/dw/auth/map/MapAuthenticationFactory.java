package life.catalogue.dw.auth.map;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.annotations.VisibleForTesting;
import life.catalogue.api.model.User;
import life.catalogue.dw.auth.AuthenticationProvider;
import life.catalogue.dw.auth.AuthenticationProviderFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.*;

/**
 * Config based authentication provider that can be used for local tests or GBIF registry independent installations.
 * Not used in production.
 *
 * The datasets key list is used for the role assigned.
 */
@JsonTypeName("map")
public class MapAuthenticationFactory implements AuthenticationProviderFactory {
  
  public static class Cred {
    @NotNull
    public String username;
    @NotNull
    public String password;
    public User.Role role;
    public List<Integer> datasets;
  }

  @Valid
  @NotNull
  public List<Cred> users = new ArrayList<>();
  
  
  @Override
  public AuthenticationProvider createAuthenticationProvider() {
    return new MapAuthentication(this);
  }
  
  public static class MapAuthentication implements AuthenticationProvider {
    private final static Logger LOG = LoggerFactory.getLogger(MapAuthentication.class);
    private final Map<String, Cred> users = new HashMap<>();
  
    public MapAuthentication(MapAuthenticationFactory cfg) {
      for (Cred c : cfg.users) {
        users.put(c.username, c);
      }
      LOG.info("In memory map authentication created with {} users", users.size());
    }
  
    @Override
    public Optional<User> authenticate(String username, String password) {
      if (username != null && password != null && users.containsKey(username)) {
        Cred c = users.get(username);
        if (c.password.equals(password)) {
          User u = new User();
          u.setUsername(c.username);
          u.setLastname(c.username);
          if (c.role != null) {
            u.getRoles().add(c.role);
            if (c.datasets != null && c.role != User.Role.ADMIN) {
              c.datasets.forEach(dk -> u.addDatasetRole(c.role, dk));
            }
          }
          return Optional.of(u);
        }
      }
      return Optional.empty();
    }
  
    @VisibleForTesting
    public Map<String, Cred> getUsers() {
      return users;
    }
  
    @Override
    public void setClient(CloseableHttpClient http) {
    }
  }
}