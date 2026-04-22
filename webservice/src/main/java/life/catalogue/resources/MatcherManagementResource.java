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
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Path("/admin")
@Hidden
@Produces(MediaType.APPLICATION_JSON)
public class MatcherManagementResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(MatcherManagementResource.class);
  private final SqlSessionFactory factory;
  private final UsageMatcherFactory matcherFactory;

  public MatcherManagementResource(SqlSessionFactory factory, UsageMatcherFactory matcherFactory) {
    this.factory = factory;
    this.matcherFactory = matcherFactory;
  }

  @GET
  @Path("/matcher")
  public UsageMatcherFactory.FactoryMetadata listMatcher(@QueryParam("decorate") boolean decorate) {
    return matcherFactory.metadata(decorate);
  }

  @GET
  @Path("/matcher/{key}")
  public UsageMatcherFactory.MatcherMetadata matcherMetadata(@PathParam("key") int key) {
    return matcherFactory.metadata(key);
  }

  @DELETE
  @Path("/matcher/{key}")
  @RolesAllowed({Roles.ADMIN})
  public void removeMatcher(@PathParam("key") int key) {
    matcherFactory.remove(key);
  }

  @POST
  @Path("/matcher/{key}")
  public BackgroundJob buildMatcher(@PathParam("key") int key, @Auth User user) throws IOException {
    LOG.info("User {} request new matcher for dataset {}", user, key);
    return matcherFactory.prepare(key, user.getKey());
  }

  @POST
  @Path("/matcher/reload")
  @RolesAllowed({Roles.ADMIN})
  public int reloadMatcher() {
    return matcherFactory.reload();
  }

}
