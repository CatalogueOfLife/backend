package life.catalogue.dw.auth;

import life.catalogue.api.model.User;
import org.apache.http.impl.client.CloseableHttpClient;

import java.util.Optional;

public interface AuthenticationProvider {
  
  Optional<User> authenticate(String username, String password);
  
  void setClient(CloseableHttpClient http);
  
}
