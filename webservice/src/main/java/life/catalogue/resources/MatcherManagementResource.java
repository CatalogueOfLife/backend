package life.catalogue.resources;

import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import life.catalogue.api.model.User;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.dw.auth.Roles;
import life.catalogue.matching.UsageMatcherFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Hidden
@Path("/matcher")
@Produces(MediaType.APPLICATION_JSON)
public class MatcherManagementResource {

  private static final Logger LOG = LoggerFactory.getLogger(MatcherManagementResource.class);
  private final UsageMatcherFactory matcherFactory;

  public MatcherManagementResource(UsageMatcherFactory matcherFactory) {
    this.matcherFactory = matcherFactory;
  }

  @GET
  @Path("{key}")
  public UsageMatcherFactory.MatcherMetadata matcherMetadata(@PathParam("key") int key) {
    return matcherFactory.metadata(key);
  }

  @POST
  @Path("rebuild")
  @RolesAllowed({Roles.ADMIN})
  public void rebuildAll(@Auth User user) {
    LOG.info("User {} requested rebuild of all matchers", user);
    matcherFactory.reconcile(true, user.getKey());
  }

  @POST
  @Path("{key}")
  @RolesAllowed({Roles.ADMIN})
  public BackgroundJob rebuildMatcher(@PathParam("key") int key, @Auth User user) {
    LOG.info("User {} requested rebuild of matcher for dataset {}", user, key);
    return matcherFactory.rebuild(key, user.getKey());
  }

}
