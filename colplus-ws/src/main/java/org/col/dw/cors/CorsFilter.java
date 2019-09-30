package org.col.dw.cors;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;

public class CorsFilter implements ContainerResponseFilter {
  
  private final CorsConfiguration cfg;
  
  public CorsFilter(CorsConfiguration cfg) {
    this.cfg = cfg;
  }
  
  @Override
  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
    responseContext.getHeaders().add("Access-Control-Allow-Origin", cfg.origins);
    responseContext.getHeaders().add("Access-Control-Allow-Methods", cfg.methods);
    responseContext.getHeaders().add("Access-Control-Allow-Headers", cfg.headers);
    if (!cfg.anyOrigin()) {
      //W3C CORS spec http://www.w3.org/TR/cors/#resource-implementation
      responseContext.getHeaders().add("Vary", "Origin");
    }
  }
}
