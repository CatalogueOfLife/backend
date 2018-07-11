package org.col.dw.jersey.filter;

import java.io.IOException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Response;

/**
 * Filter that returns a 404 instead of 204 for null results with GET requests.
 */
public class Null404ResponseFilter implements ContainerResponseFilter {

  @Override
  public void filter(ContainerRequestContext request, ContainerResponseContext response) throws IOException {
    if (!response.hasEntity()
          && request.getMethod() != null && "get".equalsIgnoreCase(request.getMethod())
          && (response.getStatusInfo() == null || response.getStatusInfo().getFamily().equals(Response.Status.Family.SUCCESSFUL))
    ) {
      throw new NotFoundException();
    }
  }
}
