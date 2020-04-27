package life.catalogue.dw.jersey.filter;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

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
