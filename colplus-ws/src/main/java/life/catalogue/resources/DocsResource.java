package life.catalogue.resources;

import java.net.URI;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import life.catalogue.WsServerConfig;

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
    return Response.status(Response.Status.MOVED_PERMANENTLY).location(raml).build();
  }
  
  @GET
  @Path("/version")
  @Produces(MediaType.TEXT_PLAIN)
  public String version() {
    return version;
  }
  
}
