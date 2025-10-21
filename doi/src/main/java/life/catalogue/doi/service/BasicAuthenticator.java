package life.catalogue.doi.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.google.common.io.BaseEncoding;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.MultivaluedMap;

public class BasicAuthenticator implements ClientRequestFilter {
  public static final String AUTHORIZATION_HEADER = "Authorization";
  private final String auth;

  public BasicAuthenticator(String user, String password) {
    this.auth = basicAuthentication(user, password);
  }

  public static String basicAuthentication(String username, String password){
    return basicAuthentication(username + ":" + password);
  }

  public static String basicAuthentication(String credentials){
    return "Basic " +  BaseEncoding.base64().encode(credentials.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public void filter(ClientRequestContext requestContext) throws IOException {
    MultivaluedMap<String, Object> headers = requestContext.getHeaders();
    headers.add(AUTHORIZATION_HEADER, auth);

  }

}