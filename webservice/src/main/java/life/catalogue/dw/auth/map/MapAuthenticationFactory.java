package life.catalogue.dw.auth.map;

import java.util.*;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.annotations.VisibleForTesting;
import org.apache.http.impl.client.CloseableHttpClient;
import life.catalogue.api.model.User;
import life.catalogue.dw.auth.AuthenticationProvider;
import life.catalogue.dw.auth.AuthenticationProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration for the GBIF IdentityService.
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
          }
          if (c.datasets != null) {
            c.datasets.forEach(u::addDataset);
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