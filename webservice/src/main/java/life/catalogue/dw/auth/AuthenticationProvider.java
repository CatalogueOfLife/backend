package life.catalogue.dw.auth;

import java.util.Optional;

import org.apache.http.impl.client.CloseableHttpClient;
import life.catalogue.api.model.User;

public interface AuthenticationProvider {
  
  Optional<User> authenticate(String username, String password);
  
  void setClient(CloseableHttpClient http);
  
}
