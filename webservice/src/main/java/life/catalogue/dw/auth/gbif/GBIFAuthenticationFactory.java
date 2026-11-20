package life.catalogue.dw.auth.gbif;

import life.catalogue.dw.auth.AuthenticationProvider;
import life.catalogue.dw.auth.AuthenticationProviderFactory;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonTypeName;

import jakarta.validation.constraints.NotNull;

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
   * Signs requests with the old HMAC-SHA1 scheme of gbif-common-ws before 2.1.4, for a GBIF API that has not been
   * upgraded yet. GBIF APIs from 2.1.4 on only accept the new scheme, the default.
   */
  public boolean legacySigning = false;

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