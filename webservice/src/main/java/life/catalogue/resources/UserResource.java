package life.catalogue.resources;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import io.dropwizard.auth.Auth;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Page;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.dw.auth.IdentityService;
import life.catalogue.dw.auth.Roles;
import org.apache.ibatis.session.SqlSession;
import life.catalogue.api.model.User;
import life.catalogue.db.mapper.UserMapper;
import life.catalogue.dw.auth.JwtCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/user")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class UserResource {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(UserResource.class);
  
  private final JwtCodec jwt;
  private final IdentityService idService;

  public UserResource(JwtCodec jwt, IdentityService idService) {
    this.jwt = jwt;
    this.idService = idService;
  }

  /**
   * obfuscate email and personal things
   */
  private static User obfuscate(User u) {
    u.setEmail(null);
    u.setSettings(null);
    u.setOrcid(null);
    u.setLastLogin(null);
    u.setCreated(null);
    u.setDeleted(null);
    u.setRoles(null);
    return u;
  }

  private static Page defaultPage(Page page){
    return page == null ? new Page(0, 10) : page;
  }

  @GET
  public List<User> search(@QueryParam("q") String q, @Context SqlSession session, @Valid @BeanParam Page page) {
    List<User> user = session.getMapper(UserMapper.class).search(q, defaultPage(page));
    user.forEach(UserResource::obfuscate);
    return user;
  }

  @GET
  @Path("/{key}")
  public User get(@PathParam("key") Integer key, @Context SqlSession session) {
    User u = session.getMapper(UserMapper.class).get(key);
    // obfuscate email and personal things
    return obfuscate(u);
  }

  @GET
  @Path("/me")
  @PermitAll
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
  @PermitAll
  public void updateSettings(Map<String, String> settings, @Auth User user, @Context SqlSession session) {
    if (user != null && settings != null) {
      user.setSettings(settings);
      session.getMapper(UserMapper.class).update(user);
      session.commit();
    }
  }

  @GET
  @Path("/me/dataset")
  @PermitAll
  public List<Dataset> datasets(@Auth User user, @Context SqlSession session) {
    return listDatasetsByUser(session, user);
  }

  @GET
  @Path("/{key}/dataset")
  @RolesAllowed({Roles.ADMIN})
  public List<Dataset> datasetsByUser(@PathParam("key") Integer key, @Context SqlSession session) {
    return listDatasetsByUser(session, getUser(session, key));
  }

  @POST
  @Path("/{key}/dataset")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void addEditor(@PathParam("key") int key, @Auth User editor, int datasetKey, @Context SqlSession session) {
    if (!editor.isAuthorized(datasetKey)) {
      throw new WebApplicationException(Response.Status.FORBIDDEN);
    }
    User user = getUser(session, key);
    // this also adds an editor role if not there yet
    user.addDataset(datasetKey);
    updateUser(session, user);
  }

  @DELETE
  @Path("/{key}/dataset/{datasetKey}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void removeEditor(@PathParam("key") int key, @PathParam("datasetKey") int datasetKey, @Auth User editor, @Context SqlSession session) {
    User user = getUser(session, key);
    // this also removes the editor role if its the only dataset privilege
    user.removeDataset(datasetKey);
    updateUser(session, user);
  }

  private List<Dataset> listDatasetsByUser(SqlSession session, User user){
    List<Dataset> datasets = new ArrayList<>();
    DatasetMapper dm = session.getMapper(DatasetMapper.class);
    for (int datasetKey : user.getDatasets()) {
      Dataset d = dm.get(datasetKey);
      if (d != null) {
        datasets.add(d);
      }
    }
    return datasets;
  }

  private void updateUser(SqlSession session, User user){
    session.getMapper(UserMapper.class).update(user);;
    session.commit();
    idService.cache(user);
  }

  private static User getUser(SqlSession session, int key) throws NotFoundException {
    UserMapper um = session.getMapper(UserMapper.class);
    User user = um.get(key);
    if (user == null) {
      throw NotFoundException.keyNotFound(User.class, key);
    }
    return user;
  }
}
