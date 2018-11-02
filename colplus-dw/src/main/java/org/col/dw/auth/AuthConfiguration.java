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
  @NotNull
  public String gbifApp = "col.app";
  
  /**
   * GBIF trusted application secret to talk to the GBIF Identity Service
   */
  @NotNull
  public String gbifSecret;

  /**
   *  Proxied user for the GBIF trusted application authorization
   */
  public String gbifUser = "col";

  /**
   * Jason Web Token used to trust in externally authenticated users.
   */
  @NotNull
  public String signingKey = "bhc564c76VT-d/722mc";
  
}