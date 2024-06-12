package life.catalogue.doi.service;

import java.io.IOException;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;

public class UserAgentFilter implements ClientRequestFilter {
  private final String userAgent;

  public UserAgentFilter() {
    userAgent = "col/1.0";
  }

  public UserAgentFilter(String userAgent) {
    this.userAgent = userAgent;
  }

  @Override
  public void filter(ClientRequestContext ctxt) throws IOException {
    ctxt.getHeaders().add(HttpHeaders.USER_AGENT, userAgent);
  }
}
