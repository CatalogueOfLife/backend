package org.col.dw.auth.gbif;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.col.dw.auth.AuthenticationProviderFactory;
import org.col.dw.auth.AuthenticationProvider;

/**
 * Configuration for the GBIF IdentityService.
 */
@JsonTypeName("gbif")
public class GBIFAuthenticationFactory implements AuthenticationProviderFactory {
  
  
  @NotNull
  public String api = "https://api.gbif.org/v1/";
  
  /**
   * Proxied GBIF user to talk to the GBIF Identity Service that needs to have REGISTRY_ADMIN rights
   */
  @NotNull
  public String user = "colplus";
  
  /**
   * GBIF trusted application key to talk to the GBIF Identity Service
   */
  @NotNull
  public String appkey = "col.app";
  
  /**
   * GBIF trusted application secret to talk to the GBIF Identity Service
   */
  @NotNull
  public String secret;
  
  
  @Override
  public AuthenticationProvider createAuthenticationProvider() {
    return new GBIFAuthentication(this);
  }
}