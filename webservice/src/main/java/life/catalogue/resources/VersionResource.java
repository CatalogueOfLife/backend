package life.catalogue.resources;

import java.time.LocalDateTime;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 *
 */
@Path("/version")
public class VersionResource {
  private final String version;
  private final LocalDateTime startup;

  public VersionResource(String version, LocalDateTime startup) {
    this.version = version;
    this.startup = startup;
  }

  @GET
  @Produces(MediaType.TEXT_PLAIN)
  public String version() {
    return version;
  }

  @GET
  @Path("/startup")
  @Produces(MediaType.TEXT_PLAIN)
  public String startup() {
    return startup.toString();
  }
}
