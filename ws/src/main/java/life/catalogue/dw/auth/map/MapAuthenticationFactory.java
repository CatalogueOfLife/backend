package life.catalogue.dw.auth.map;

import java.util.*;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.annotations.VisibleForTesting;
import org.apache.http.impl.client.CloseableHttpClient;
import life.catalogue.api.model.ColUser;
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
    public ColUser.Role role;
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
    public Optional<ColUser> authenticate(String username, String password) {
      if (username != null && password != null && users.containsKey(username)) {
        Cred c = users.get(username);
        if (c.password.equals(password)) {
          ColUser u = new ColUser();
          u.setUsername(c.username);
          u.setLastname(c.username);
          u.getRoles().add(ColUser.Role.USER);
          if (c.role != null) {
            u.getRoles().add(c.role);
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