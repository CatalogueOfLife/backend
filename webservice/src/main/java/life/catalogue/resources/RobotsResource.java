package life.catalogue.resources;

import life.catalogue.common.io.Resources;

import java.io.IOException;
import java.io.InputStream;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 *
 */
@Path("/")
public class RobotsResource {

  @GET
  @Path("/robots")
  @Produces(MediaType.TEXT_PLAIN)
  public InputStream robots2() {
    return Resources.stream("robots.txt");
  }

}
