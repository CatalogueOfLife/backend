package life.catalogue.resources;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.model.User;
import life.catalogue.dao.UserDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.dw.auth.AuthFilter;
import life.catalogue.dw.auth.JwtCodec;
import life.catalogue.dw.auth.Roles;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.auth.Auth;

@Path("/user")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class UserResource {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(UserResource.class);
  
  private final JwtCodec jwt;
  private final UserDao dao;

  public UserResource(JwtCodec jwt, UserDao dao) {
    this.jwt = jwt;
    this.dao = dao;
  }

  /**
   * obfuscate email and personal things
   */
  private static User obfuscate(User u) {
    u.setEmail(null);
    u.setSettings(null);
    u.setLastLogin(null);
    return u;
  }

  @GET
  public ResultPage<User> search(@QueryParam("q") String q, @Valid @BeanParam Page page, @Auth User user) {
    ResultPage<User> users = dao.search(q, page);
    if (!user.isAdmin()) {
    }    users.forEach(UserResource::obfuscate);
    return users;
  }

  @GET
  @Path("/{key}")
  public User get(@PathParam("key") Integer key) {
    User user = getUser(key);
    // obfuscate email and personal things
    return obfuscate(user);
  }

  @GET
  @Path("/me")
  public User me(@Auth User user) {
    return user;
  }
  
  /**
   * Makes surer a user has authenticated with BasicAuth and then returns a new JWT token if successful.
   */
  @GET
  @Path("/login")
  public String login(@Context SecurityContext secCtxt, @Auth User user) {
    // the user shall be authenticated using basic auth scheme only.
    if (secCtxt == null || !SecurityContext.BASIC_AUTH.equalsIgnoreCase(secCtxt.getAuthenticationScheme())) {
      throw new WebApplicationException(Response.Status.FORBIDDEN);
    }
    if (user == null) {
      throw new WebApplicationException(Response.Status.UNAUTHORIZED);
    }
    return jwt.generate(user);
  }
  
  @PUT
  @Path("/settings")
  public void updateSettings(Map<String, String> settings, @Auth User user) {
    dao.updateSettings(settings, user);
  }

  @GET
  @Path("/dataset")
  public List<Dataset> datasets(@Auth User user, @Context SqlSession session) {
    final DatasetMapper dm = session.getMapper(DatasetMapper.class);
    return user.getEditor().stream()
               .map(dm::get)
               .collect(Collectors.toList());
  }

  @GET
  @Path("/dataset/{key}")
  public boolean isAuthorized(@PathParam("key") int key, @Auth User user) {
    return AuthFilter.hasReadAccess(user, key);
  }

  @PUT
  @Path("/{key}/role")
  @RolesAllowed({Roles.ADMIN})
  public void changeRole(@PathParam("key") int key, @Auth User admin, List<User.Role> roles) {
    dao.changeRole(key, admin, roles);
  }

  @POST
  @Path("/{key}/block")
  @RolesAllowed({Roles.ADMIN})
  public void block(@PathParam("key") int key, @Auth User admin) {
    dao.block(key, admin);
  }

  @DELETE
  @Path("/{key}/block")
  @RolesAllowed({Roles.ADMIN})
  public void unblock(@PathParam("key") int key, @Auth User admin) {
    dao.unblock(key, admin);
  }

  private User getUser(int key) throws NotFoundException {
    User user = dao.get(key);
    if (user == null) {
      throw NotFoundException.notFound(User.class, key);
    }
    return user;
  }
}
