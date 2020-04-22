package life.catalogue.dw.cors;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.container.*;
import javax.ws.rs.core.Response;
import java.io.IOException;

/**
 * Handles CORS requests both preflight and simple CORS requests.
 * See W3C CORS spec http://www.w3.org/TR/cors/#resource-implementation
 */
@PreMatching
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

  private static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
  private static final String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";
  private static final String ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";
  private static final String ACCESS_CONTROL_MAX_AGE = "Access-Control-Max-Age";
  private static final String ORIGIN = "Origin";
  private static final String ACCESS_CONTROL_REQUEST_METHOD = "Access-Control-Request-Method";
  private static final String ACCESS_CONTROL_EXPOSE_HEADERS = "Access-Control-Expose-Headers";
  private static final String ACCESS_CONTROL_REQUEST_HEADERS = "Access-Control-Request-Headers";
  private static final String VARY = "Vary";
  private static final String FAILURE_PROP = "cors.failure";

  private final CorsConfiguration cfg;

  public CorsFilter(CorsConfiguration cfg) {
    this.cfg = cfg;
  }

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String origin = requestContext.getHeaderString(ORIGIN);
    if (origin == null) {
      return;
    }
    if (requestContext.getMethod().equalsIgnoreCase(HttpMethod.OPTIONS)) {
      preflight(origin, requestContext);
    } else {
      checkOrigin(requestContext, origin);
    }
  }

  @Override
  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
    String origin = requestContext.getHeaderString(ORIGIN);
    if (origin == null || requestContext.getMethod().equalsIgnoreCase(HttpMethod.OPTIONS) || requestContext.getProperty(FAILURE_PROP) != null) {
      // don't do anything if origin is null, its an OPTIONS request, or cors.failure is set
      return;
    }
    responseContext.getHeaders().putSingle(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
    responseContext.getHeaders().putSingle(VARY, ORIGIN);
    if (cfg.exposedHeaders != null) {
      responseContext.getHeaders().putSingle(ACCESS_CONTROL_EXPOSE_HEADERS, cfg.exposedHeaders);
    }
  }

  protected void preflight(String origin, ContainerRequestContext requestContext) throws IOException {
    checkOrigin(requestContext, origin);

    Response.ResponseBuilder builder = Response.ok();
    builder.header(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
    builder.header(VARY, ORIGIN);
    String requestMethods = requestContext.getHeaderString(ACCESS_CONTROL_REQUEST_METHOD);
    if (requestMethods != null) {
      if (cfg.methods != null) {
        requestMethods = cfg.methods;
      }
      builder.header(ACCESS_CONTROL_ALLOW_METHODS, requestMethods);
    }
    String allowHeaders = requestContext.getHeaderString(ACCESS_CONTROL_REQUEST_HEADERS);
    if (allowHeaders != null) {
      if (cfg.headers != null) {
        allowHeaders = cfg.headers;
      }
      builder.header(ACCESS_CONTROL_ALLOW_HEADERS, allowHeaders);
    }
    if (cfg.maxAge > -1) {
      builder.header(ACCESS_CONTROL_MAX_AGE, cfg.maxAge);
    }
    requestContext.abortWith(builder.build());
  }

  protected void checkOrigin(ContainerRequestContext requestContext, String origin) {
    if (!cfg.origins.contains(CorsConfiguration.ANY_ORIGIN) && !cfg.origins.contains(origin)) {
      requestContext.setProperty(FAILURE_PROP, true);
      throw new ForbiddenException("Origin " + origin + " not allowed");
    }
  }
}
