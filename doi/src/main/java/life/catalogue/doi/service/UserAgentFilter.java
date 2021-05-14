package life.catalogue.doi.service;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.HttpHeaders;

import java.io.IOException;

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
