package org.col.dw.auth;

import javax.validation.constraints.NotNull;

/**
 * Configuration for JWT and Basic authentication
 * backed by the GBIF IdentityService.
 */
public class AuthConfiguration {
  
  
  @NotNull
  public String gbifApi = "https://api.gbif.org/v1/";
  
  /**
   * GBIF trusted application key to talk to the GBIF Identity Service
   */
  public String gbifSecret;
  
  /**
   * Jason Web Token used to trust in externally authenticated users.
   */
  @NotNull
  public String signingKey = "1234567890";
  
}