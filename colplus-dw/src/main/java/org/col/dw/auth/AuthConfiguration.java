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
   * Proxied GBIF user to talk to the GBIF Identity Service that needs to have REGISTRY_ADMIN rights
   */
  @NotNull
  public String gbifUser = "colplus";
  
  /**
   * GBIF trusted application key to talk to the GBIF Identity Service
   */
  @NotNull
  public String gbifAppkey = "col.app";
  
  /**
   * GBIF trusted application secret to talk to the GBIF Identity Service
   */
  @NotNull
  public String gbifSecret;
  
  /**
   * Jason Web Token used to trust in externally authenticated users.
   */
  @NotNull
  public String signingKey = "bhc564c76VT-d/722mc";
  
}