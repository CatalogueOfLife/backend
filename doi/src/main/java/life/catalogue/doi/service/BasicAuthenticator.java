package life.catalogue.doi.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.MultivaluedMap;

import com.google.common.io.BaseEncoding;

public class BasicAuthenticator implements ClientRequestFilter {

  private final String auth;

  public BasicAuthenticator(String user, String password) {
    this.auth = basicAuthentication(user, password);
  }

  public static String basicAuthentication(String username, String password){
    String cred = username + ":" + password;
    return "Basic " +  BaseEncoding.base64().encode(cred.getBytes(StandardCharsets.UTF_8));
  }

  public void filter(ClientRequestContext requestContext) throws IOException {
    MultivaluedMap<String, Object> headers = requestContext.getHeaders();
    headers.add("Authorization", auth);

  }

}