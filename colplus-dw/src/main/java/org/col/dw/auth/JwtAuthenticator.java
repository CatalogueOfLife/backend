package org.col.dw.auth;

import java.util.Optional;
import javax.ws.rs.NotAuthorizedException;

import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import org.col.api.model.ColUser;

/**
 * Dropwizard authenticator that validates if the user has been authenticated against a Jason Web Token.
 * Once the JWT is decoded and property 'userName' is examined to determine if it represents a valid user.
 */
public class JwtAuthenticator implements Authenticator<String, ColUser> {
  
  //Private secure key used to encode/decode the JWT.
  private final JwtCoder jwt;
  private final IdentityService idService;
  
  public JwtAuthenticator(JwtCoder jwt, IdentityService idService) {
    this.idService = idService;
    this.jwt = jwt;
  }
  
  /**
   * Authenticates a user represented as encoded JWT token in the credentials parameter.
   * The ColUser exists in postgres in this case and can be loaded by the id service.
   * @param credentials encoded string containing the JWT token
   * @return a ColUser if present or NotAuthorizedException if the credential are invalid
   * @throws AuthenticationException in case of error
   */
  @Override
  public Optional<ColUser> authenticate(String credentials) throws AuthenticationException {
    try {
      Jws<Claims> jws = jwt.parse(credentials);
      return Optional.of(Integer.valueOf(jws.getBody().getSubject()))
          .map(idService::get);
    
    } catch (JwtException | IllegalArgumentException ex) {
      StringBuilder sb = new StringBuilder("Invalid JWT token");
      if (ex.getMessage() != null) {
        sb.append(": ").append(ex.getMessage());
      }
      throw new NotAuthorizedException(sb.toString(), ColUnauthorizedHandler.challengeJwt(AuthBundle.REALM));
    } catch (Exception ex) {
      throw new AuthenticationException("JWT token could not be verified", ex);
    }
  }
  

}