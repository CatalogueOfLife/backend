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
 * Filter that adds an Accept header with application/json
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
    "html", MediaType.TEXT_HTML,
    "tsv", MoreMediaTypes.TEXT_TSV,
    "csv", MoreMediaTypes.TEXT_CSV,
    "png", MoreMediaTypes.IMG_PNG,
    "image", MoreMediaTypes.IMG_PNG
  );

  @Override
  public void filter(ContainerRequestContext req)  {
    if (req.getUriInfo().getQueryParameters().containsKey(ACCEPT_PARAM)) {
      String val = req.getUriInfo().getQueryParameters().getFirst(ACCEPT_PARAM);
      req.getHeaders().putSingle(HttpHeaders.ACCEPT, VALUE_MAP.getOrDefault(val.toLowerCase(), val));
    }
  }

}
