package life.catalogue.resources;

import life.catalogue.api.model.NameMatch;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

@Path("/name/matching")
@Produces(MediaType.APPLICATION_JSON)
@Deprecated
public class NameMatchingResource {

  /**
   * Parsing citations as GET query parameters.
   */
  @GET
  public NameMatch match(@Context UriInfo uri) {
    // redirect to names index resource
    var newURI = uri.getRequestUriBuilder().replacePath("/").path(NamesIndexResource.class).path("match").build();
    throw ResourceUtils.redirect(newURI);
  }

}
