package life.catalogue.dw.jersey.filter;

import java.io.IOException;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.ext.Provider;

@Provider
@VaryAccept
public class VaryAcceptResponseFilter implements ContainerResponseFilter {
  
  @Override
  public void filter(ContainerRequestContext request, ContainerResponseContext resp) throws IOException {
    resp.getHeaders().putSingle(HttpHeaders.VARY, HttpHeaders.ACCEPT);
  }
}
