package life.catalogue.resources;

import io.dropwizard.auth.Auth;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.User;
import life.catalogue.dao.AuthorizationDao;
import life.catalogue.dao.DatasetDao;
import life.catalogue.db.mapper.UserMapper;
import life.catalogue.dw.auth.Roles;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;

import java.util.List;

@SuppressWarnings("static-method")
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
    return dao.list(datasetKey, role);
  }

  @POST
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void add(@PathParam("key") int datasetKey, int userKey, @Auth User user) {
    dao.add(datasetKey, userKey, role, user);
  }

  @DELETE
  @Path("{id}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void remove(@PathParam("key") int datasetKey, @PathParam("id") int userKey, @Auth User user) {
    dao.remove(datasetKey, userKey, role, user);
  }

}
