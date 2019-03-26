package org.col.dw.auth;

import java.util.Optional;

import org.apache.http.impl.client.CloseableHttpClient;
import org.col.api.model.ColUser;

public interface AuthenticationProvider {
  
  Optional<ColUser> authenticate(String username, String password);
  
  void setClient(CloseableHttpClient http);
  
}
