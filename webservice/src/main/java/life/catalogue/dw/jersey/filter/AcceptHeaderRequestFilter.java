package life.catalogue.dw.jersey.filter;

import life.catalogue.dw.jersey.MoreMediaTypes;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;
import java.util.Map;

/**
 * Filter that adds an Accept and Content-Type header with application/json
 * in case none is given or the value allows any (*).
 *
 * It also allows to set a custom Accept header via an Accept query parameter value.
 */
@Provider
@PreMatching
public class AcceptHeaderRequestFilter implements ContainerRequestFilter {
  private final static String ACCEPT_PARAM = "accept";
  private final static Map<String, String> VALUE_MAP = Map.of(
    "xml", MediaType.APPLICATION_XML,
    "json", MediaType.APPLICATION_JSON,
    "text", MediaType.TEXT_PLAIN,
    "tsv", MoreMediaTypes.TEXT_TSV,
    "csv", MoreMediaTypes.TEXT_CSV
  );

  @Override
  public void filter(ContainerRequestContext req)  {
    if (req.getUriInfo().getQueryParameters().containsKey(ACCEPT_PARAM)) {
      String val = req.getUriInfo().getQueryParameters().getFirst(ACCEPT_PARAM);
      req.getHeaders().putSingle(HttpHeaders.ACCEPT, VALUE_MAP.getOrDefault(val.toLowerCase(), val));
    } else if (!req.getHeaders().containsKey(HttpHeaders.CONTENT_TYPE) && (
      !req.getHeaders().containsKey(HttpHeaders.ACCEPT) || req.getHeaders().getFirst(HttpHeaders.ACCEPT).equals("*/*")
    )) {
      req.getHeaders().putSingle(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON);
      if (req.getLength() > 0) {
        req.getHeaders().putSingle(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
      }
    }
  }

}
