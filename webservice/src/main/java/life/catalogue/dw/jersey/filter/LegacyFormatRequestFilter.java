package life.catalogue.dw.jersey.filter;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

/**
 * Legacy API Filter to apply Accept header by looking at a "format" parameter
 * To change the Accept header this filter needs to be a prematching filter that must be bound globally.
 * We therefore restrict the application within the filter by checking the path.
 */
@Provider
@PreMatching
public class LegacyFormatRequestFilter implements ContainerRequestFilter {

  private static final String PARAM  = "format";

  @Override
  public void filter(ContainerRequestContext req) throws IOException {
    if (req.getUriInfo().getPath().contains("/legacy")) {
      MultivaluedMap<String, String> params = req.getUriInfo().getQueryParameters();
      if (params.containsKey(PARAM) && params.getFirst(PARAM).endsWith("json")) {
        req.getHeaders().putSingle(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON);
      } else {
        // XML is the legacy default
        req.getHeaders().putSingle(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML);
      }
    }

  }
}
