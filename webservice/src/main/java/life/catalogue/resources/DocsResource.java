package life.catalogue.resources;

import life.catalogue.WsServerConfig;
import life.catalogue.common.io.Resources;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;

/**
 *
 */
@Path("/")
public class DocsResource {
  private final String version;
  
  public DocsResource(WsServerConfig cfg) {
    version = cfg.versionString();
  }
  
  @GET
  @Produces(MediaType.TEXT_HTML)
  public InputStream docs() throws IOException {
    return Resources.stream("swagger.html");
  }
  
  @GET
  @Path("/version")
  @Produces(MediaType.TEXT_PLAIN)
  public String version() {
    return version;
  }
  
}
