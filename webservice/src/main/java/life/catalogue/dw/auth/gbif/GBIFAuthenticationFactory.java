package life.catalogue.dw.auth.gbif;

import life.catalogue.dw.auth.AuthenticationProvider;
import life.catalogue.dw.auth.AuthenticationProviderFactory;

import javax.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonTypeName;

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
  
  /**
   * Test username to use to verify the GBIF authentication is working fine
   */
  @Nullable
  public String verificationUser;

  @Override
  public AuthenticationProvider createAuthenticationProvider() {
    return new GBIFAuthentication(this);
  }
}