package life.catalogue.resources;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import io.dropwizard.auth.Auth;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.Dataset;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.dw.auth.IdentityService;
import life.catalogue.dw.auth.Roles;
import org.apache.ibatis.session.SqlSession;
import life.catalogue.api.model.ColUser;
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

  @GET
  @Path("/{key}")
  public ColUser get(@PathParam("key") Integer key, @Context SqlSession session) {
    ColUser u = session.getMapper(UserMapper.class).get(key);
    // obfuscate email and personal things
    if (u != null) {
      u.setEmail(null);
      u.setSettings(null);
      u.setOrcid(null);
      u.setLastLogin(null);
      u.setCreated(null);
      u.setDeleted(null);
      u.setRoles(null);
    }
    return u;
  }

  @GET
  @Path("/me")
  @PermitAll
  public ColUser me(@Auth ColUser user) {
    return user;
  }
  
  /**
   * Makes surer a user has authenticated with BasicAuth and then returns a new JWT token if successful.
   */
  @GET
  @Path("/login")
  public String login(@Context SecurityContext secCtxt, @Auth ColUser user) {
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
  public void updateSettings(Map<String, String> settings, @Auth ColUser user, @Context SqlSession session) {
    if (user != null && settings != null) {
      user.setSettings(settings);
      session.getMapper(UserMapper.class).update(user);
      session.commit();
    }
  }

  @GET
  @Path("/me/dataset")
  @PermitAll
  public List<Dataset> datasets(@Auth ColUser user, @Context SqlSession session) {
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
  public void addDatasetKey(@PathParam("key") int key, @Auth ColUser editor, int datasetKey, @Context SqlSession session) {
    if (!editor.isAuthorized(datasetKey)) {
      throw new WebApplicationException(Response.Status.FORBIDDEN);
    }
    ColUser user = getUser(session, key);
    user.addDataset(datasetKey);
    updateUser(session, user);
  }

  @DELETE
  @Path("/{key}/dataset/{datasetKey}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void removeDatasetKey(@PathParam("key") int key, @PathParam("datasetKey") int datasetKey, @Auth ColUser editor, @Context SqlSession session) {
    ColUser user = getUser(session, key);
    user.removeDataset(datasetKey);
    updateUser(session, user);
  }

  private List<Dataset> listDatasetsByUser(SqlSession session, ColUser user){
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

  private void updateUser(SqlSession session, ColUser user){
    session.getMapper(UserMapper.class).update(user);;
    session.commit();
    idService.cache(user);
  }

  private static ColUser getUser(SqlSession session, int key) throws NotFoundException {
    UserMapper um = session.getMapper(UserMapper.class);
    ColUser user = um.get(key);
    if (user == null) {
      throw NotFoundException.keyNotFound(ColUser.class, key);
    }
    return user;
  }
}
