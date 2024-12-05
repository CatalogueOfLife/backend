package life.catalogue.dw.jersey.filter;

import java.io.IOException;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;

@Provider
@VaryAccept
public class VaryAcceptResponseFilter implements ContainerResponseFilter {
  
  @Override
  public void filter(ContainerRequestContext request, ContainerResponseContext resp) throws IOException {
    resp.getHeaders().putSingle(HttpHeaders.VARY, HttpHeaders.ACCEPT);
  }
}
