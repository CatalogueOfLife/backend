package org.col.dw.auth;


import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.dropwizard.jackson.Discoverable;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public interface AuthenticationProviderFactory extends Discoverable {
  
  AuthenticationProvider createAuthenticationProvider();

}
