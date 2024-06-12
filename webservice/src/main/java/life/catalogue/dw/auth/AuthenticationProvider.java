package life.catalogue.dw.auth;

import life.catalogue.api.model.User;

import java.util.Optional;

import org.apache.hc.core5.http.impl.client.CloseableHttpClient;

public interface AuthenticationProvider {
  
  Optional<User> authenticate(String username, String password);
  
  void setClient(CloseableHttpClient http);
  
}
