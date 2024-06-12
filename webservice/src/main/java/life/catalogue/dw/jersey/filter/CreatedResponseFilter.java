package life.catalogue.dw.jersey.filter;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.UUID;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Filter that updates http headers when a new resource is successfully created via a POST request.
 * The following headers are added or replaced if they existed:
 * <ul>
 * <li>Http response code 201</li>
 * <li>Location header is set accordingly based on returned key</li>
 * </ul>
 */
@Provider
public class CreatedResponseFilter implements ContainerResponseFilter {
  
  @Override
  public void filter(ContainerRequestContext request, ContainerResponseContext response) throws IOException {
    if (request.getMethod() != null
        && "post".equalsIgnoreCase(request.getMethod())
        && response.getStatusInfo() != null
        && response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
      
      response.setStatus(HttpURLConnection.HTTP_CREATED);
      // if response contains the key, also set Location
      if (response.getEntity() != null) {
        Object key = response.getEntity();
        // we use POSTs also for non Create method which can return large objects, e.g. a list of parsed names
        // only set the location header if the object is one of the following simple primary key data types
        // for strings we require the string to be small, as we also return long logs which are not keys
        if (key instanceof Number || key instanceof UUID || (key instanceof String && key.toString().length() <= 128)) {
          // allow POSTing to resource with or without trailing slash
          URI location = request.getUriInfo().getRequestUriBuilder().path(key.toString()).build();
          response.getHeaders().putSingle("Location", location.toString());
        }
      }
    }
  }
}
