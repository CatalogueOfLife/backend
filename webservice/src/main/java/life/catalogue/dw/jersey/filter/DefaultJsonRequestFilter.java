package life.catalogue.dw.jersey.filter;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;

/**
 * Filter that adds an Accept and Content-Type header with application/json
 * in case none is given or the value allows any (*).
 */
@Provider
@PreMatching
public class DefaultJsonRequestFilter implements ContainerRequestFilter {

  @Override
  public void filter(ContainerRequestContext req)  {
    if (!req.getHeaders().containsKey(HttpHeaders.CONTENT_TYPE) && (
      !req.getHeaders().containsKey(HttpHeaders.ACCEPT) || req.getHeaders().getFirst(HttpHeaders.ACCEPT).equals("*/*")
    )) {
      req.getHeaders().putSingle(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON);
      if (req.getLength() > 0) {
        req.getHeaders().putSingle(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
      }
    }
  }

}
