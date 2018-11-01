package org.col.dw.auth;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import io.dropwizard.auth.UnauthorizedHandler;
import org.col.dw.jersey.exception.JsonExceptionMapperBase;

public class ColUnauthorizedHandler implements UnauthorizedHandler {
  private static final String CHALLENGE_FORMAT = "%s realm=\"%s\"";
  
  @Override
  public Response buildResponse(String prefix, String realm) {
    return buildResponse(prefix, realm, "Failed to authenticate via Basic or Bearer JWT");
  }
  
  public static String challengeBasic(String realm) {
    return String.format(CHALLENGE_FORMAT, "Basic", realm);
  }
  
  public static String challengeJwt(String realm) {
    return String.format(CHALLENGE_FORMAT, "Bearer token_type=\"JWT\"", realm);
  }

  public static Response buildResponse(String prefix, String realm, String errorMessage) {
    return JsonExceptionMapperBase.jsonErrorResponseBuilder(Response.Status.UNAUTHORIZED, errorMessage)
        .header(HttpHeaders.WWW_AUTHENTICATE, challengeBasic(realm))
        // see https://tools.ietf.org/html/rfc6750#page-7
        .header(HttpHeaders.WWW_AUTHENTICATE, challengeJwt(realm))
        .build();
  }
}
