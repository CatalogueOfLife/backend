package life.catalogue.dw.jersey.filter;

import java.io.IOException;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Filter that returns a 404 instead of 204 for null results with GET requests.
 */
@Provider
public class Null404ResponseFilter implements ContainerResponseFilter {
  
  @Override
  public void filter(ContainerRequestContext request, ContainerResponseContext response) throws IOException {
    if (!response.hasEntity()
        && request.getMethod() != null && HttpMethod.GET.equalsIgnoreCase(request.getMethod())
        && (response.getStatusInfo() == null || response.getStatusInfo().getFamily().equals(Response.Status.Family.SUCCESSFUL))
        ) {
      throw new NotFoundException();
    }
  }
}
