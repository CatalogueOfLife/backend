package life.catalogue.dw.cors;

import life.catalogue.api.util.ObjectUtils;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.container.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.IOException;

/**
 * Handles CORS requests both preflight and simple (GET, POST or HEAD) CORS requests.
 * See W3C CORS spec http://www.w3.org/TR/cors/#resource-implementation
 */
@PreMatching
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

  private static final String ANY_ORIGIN = "*";
  private static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
  private static final String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";
  private static final String ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";
  private static final String ACCESS_CONTROL_MAX_AGE = "Access-Control-Max-Age";
  private static final String ORIGIN = "Origin";
  private static final String ACCESS_CONTROL_REQUEST_METHOD = "Access-Control-Request-Method";
  private static final String ACCESS_CONTROL_REQUEST_HEADERS = "Access-Control-Request-Headers";
  private static final String ACCESS_CONTROL_EXPOSE_HEADERS = "Access-Control-Expose-Headers";

  private final CorsConfiguration cfg;

  public CorsFilter(CorsConfiguration cfg) {
    this.cfg = cfg;
  }

  @Override
  public void filter(ContainerRequestContext req) throws IOException {
    if (req.getMethod().equalsIgnoreCase(HttpMethod.OPTIONS)) {
      preflight(req);
    }
  }

  @Override
  public void filter(ContainerRequestContext req, ContainerResponseContext resp) throws IOException {
    String origin = req.getHeaderString(ORIGIN);
    if (origin == null || req.getMethod().equalsIgnoreCase(HttpMethod.OPTIONS)) {
      // don't do anything if origin is null or it is an OPTIONS request
      return;
    }
    resp.getHeaders().putSingle(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
    resp.getHeaders().putSingle(HttpHeaders.VARY, ORIGIN);
    if (cfg.exposedHeaders != null) {
      resp.getHeaders().putSingle(ACCESS_CONTROL_EXPOSE_HEADERS, cfg.exposedHeaders);
    }
  }

  /**
   * Aborts the request and sends back a response so the real resources are not hit
   */
  protected void preflight(ContainerRequestContext req) throws IOException {
    Response.ResponseBuilder builder = Response.status(Response.Status.NO_CONTENT);
    String origin = req.getHeaderString(ORIGIN);
    builder.header(ACCESS_CONTROL_ALLOW_ORIGIN, ObjectUtils.coalesce(origin, ANY_ORIGIN));
    builder.header(HttpHeaders.VARY, ORIGIN);
    addHeaderIfRequested(req, builder, ACCESS_CONTROL_REQUEST_METHOD, ACCESS_CONTROL_ALLOW_METHODS, cfg.methods);
    addHeaderIfRequested(req, builder, ACCESS_CONTROL_REQUEST_HEADERS, ACCESS_CONTROL_ALLOW_HEADERS, cfg.headers);
    if (cfg.maxAge > -1) {
      builder.header(ACCESS_CONTROL_MAX_AGE, cfg.maxAge);
    }
    req.abortWith(builder.build());
  }

  private static void addHeaderIfRequested(ContainerRequestContext req, Response.ResponseBuilder builder, String requestedHeader, String header, String cfgValue){
    String reqValue = req.getHeaderString(requestedHeader);
    if (reqValue != null) {
      builder.header(header, cfgValue == null ? reqValue : cfgValue);
    }
  }

}
