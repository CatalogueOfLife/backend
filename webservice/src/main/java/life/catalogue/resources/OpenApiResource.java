package life.catalogue.resources;

import life.catalogue.WsServerConfig;
import life.catalogue.common.io.Resources;
import life.catalogue.common.ws.MoreMediaTypes;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;

import io.swagger.v3.oas.models.OpenAPI;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

/**
 *
 */
@Path("/")
public class OpenApiResource {
  private final OpenAPI openApi;

  public OpenApiResource(OpenAPI openApi) {
    this.openApi = openApi;
  }
  
  @GET
  @Produces(MediaType.TEXT_HTML)
  public InputStream docs() throws IOException {
    return Resources.stream("swagger.html");
  }

  @GET
  @Path("/openapi")
  @Produces({MediaType.APPLICATION_JSON, MoreMediaTypes.APP_YAML, MoreMediaTypes.APP_X_YAML, MoreMediaTypes.TEXT_YAML})
  public OpenAPI openApi(@Context HttpHeaders headers, @Context UriInfo uriInfo) {
    return openApi;
  }
  
}
