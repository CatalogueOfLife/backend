package life.catalogue.resources;

import java.net.URI;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import life.catalogue.WsServerConfig;
import life.catalogue.dw.jersey.Redirect;

/**
 *
 */
@Path("/")
@Produces(MediaType.TEXT_HTML)
public class DocsResource {
  private final URI raml;
  private final String version;
  
  public DocsResource(WsServerConfig cfg) {
    this.raml = URI.create(cfg.raml);
    version = cfg.versionString();
  }
  
  @GET
  public Response docs() {
    return Redirect.temporary(raml);
  }
  
  @GET
  @Path("/version")
  @Produces(MediaType.TEXT_PLAIN)
  public String version() {
    return version;
  }
  
}
