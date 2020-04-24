package life.catalogue.resources;

import life.catalogue.WsServerConfig;
import life.catalogue.common.io.Resources;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.IOException;

/**
 *
 */
@Path("/")
public class DocsResource {
  private final String openApiUrl = "http://localhost:8080/openapi.json";
  private final String version;
  
  public DocsResource(WsServerConfig cfg) {
    version = cfg.versionString();
  }
  
  @GET
  @Produces(MediaType.TEXT_HTML)
  public String docs() throws IOException {
    String html = Resources.toString("swagger.html");
    return html.replaceFirst("OPENAPI_URL", openApiUrl);
  }
  
  @GET
  @Path("/version")
  @Produces(MediaType.TEXT_PLAIN)
  public String version() {
    return version;
  }
  
}
