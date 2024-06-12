package life.catalogue.resources;

import life.catalogue.api.model.User;
import life.catalogue.dao.AuthorizationDao;
import life.catalogue.dw.auth.Roles;

import java.util.List;

import io.dropwizard.auth.Auth;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@SuppressWarnings("static-method")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AbstractDatasetUserResource {
  private final AuthorizationDao dao;
  private final User.Role role;

  public AbstractDatasetUserResource(User.Role role, AuthorizationDao dao) {
    this.dao = dao;
    this.role = role;
  }

  @GET
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public List<User> users(@PathParam("key") int datasetKey, @Auth User user) {
    return dao.listUsers(datasetKey, role);
  }

  @POST
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void add(@PathParam("key") int datasetKey, int userKey, @Auth User user) {
    dao.addUser(datasetKey, userKey, role, user);
  }

  @DELETE
  @Path("{id}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void remove(@PathParam("key") int datasetKey, @PathParam("id") int userKey, @Auth User user) {
    dao.removeUser(datasetKey, userKey, role, user);
  }

}
