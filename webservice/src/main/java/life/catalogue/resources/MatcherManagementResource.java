package life.catalogue.resources;

import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import life.catalogue.api.model.User;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.dw.auth.AuthFilter;
import life.catalogue.dw.auth.Roles;
import life.catalogue.matching.UsageMatcherFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

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
  public Map<String, Object> config() {
    return Map.of("pgMatcherThreshold", matcherFactory.getPgMatcherThreshold());
  }

  @GET
  @Path("{key}")
  public UsageMatcherFactory.MatcherMetadata matcherMetadata(@PathParam("key") int key) {
    return matcherFactory.metadata(key);
  }

  @POST
  @Path("rebuild")
  @RolesAllowed({Roles.ADMIN})
  public void rebuildAll(@QueryParam("force") boolean force, @Auth User user) {
    LOG.info("User {} requested rebuild of {} matchers", user, force ? "all" : "stale");
    matcherFactory.reconcile(force, user.getKey());
  }

  @POST
  @Path("{key}")
  @RolesAllowed({Roles.ADMIN})
  public BackgroundJob rebuildMatcher(@PathParam("key") int key, @Auth User user) {
    LOG.info("User {} requested rebuild of matcher for dataset {}", user, key);
    return matcherFactory.rebuild(key, user.getKey());
  }

  /**
   * Frees the matcher of a dataset, deleting its store and sidecar. Mostly useful for an on demand matcher
   * that only exists because someone matched against a private dataset and that should go before the
   * {@link life.catalogue.config.MatchingConfig#onDemandTtlDays} expiry reaps it.
   * Any dataset the published invariant does require gets its matcher back at the next reconcile.
   */
  @DELETE
  @Path("{key}")
  public void deleteMatcher(@PathParam("key") int key, @Auth User user) {
    // this is not a /dataset/{key} URI, so AuthFilter.isUserInRole cannot scope the EDITOR role to this
    // dataset and @RolesAllowed(EDITOR) would let an editor of any dataset through. Check it explicitly.
    if (!AuthFilter.hasWriteAccess(user, key)) {
      throw new ForbiddenException("You are not authorized to manage the matcher of dataset " + key);
    }
    LOG.info("User {} deleted the matcher for dataset {}", user, key);
    matcherFactory.remove(key);
  }

}
