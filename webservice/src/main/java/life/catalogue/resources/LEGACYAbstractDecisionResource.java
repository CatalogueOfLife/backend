package life.catalogue.resources;

import io.dropwizard.auth.Auth;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.DatasetScopedEntity;
import life.catalogue.api.model.User;
import life.catalogue.dao.DatasetEntityDao;
import life.catalogue.dw.auth.Roles;
import life.catalogue.dw.jersey.MoreHttpHeaders;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.*;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public abstract class LEGACYAbstractDecisionResource<T extends DatasetScopedEntity<Integer>> {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractGlobalResource.class);

  protected final Class<T> objClass;
  protected final DatasetEntityDao<Integer, T, ?> dao;
  protected final SqlSessionFactory factory;

  public LEGACYAbstractDecisionResource(Class<T> objClass, DatasetEntityDao<Integer, T, ?> dao, SqlSessionFactory factory) {
    this.objClass = objClass;
    this.dao = dao;
    this.factory = factory;
  }

  void warn(UriInfo uri, HttpHeaders headers){
    LOG.warn("Legacy resource used: {} from {}", uri.getAbsolutePath(), headers.getHeaderString(MoreHttpHeaders.REFERER));
  }

  /**
   * @return the primary key of the object. Together with the CreatedResponseFilter will return a 201 location
   */
  @POST
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public Integer create(@Valid T obj, @Auth User user, @Context UriInfo uri, @Context HttpHeaders headers) {
    warn(uri, headers);
    obj.applyUser(user);
    return dao.create(obj, user.getKey()).getId();
  }

  @GET
  @Path("{key}")
  public T get(@PathParam("key") Integer key, @Context UriInfo uri, @Context HttpHeaders headers) {
    warn(uri, headers);
    T obj = dao.get(DSID.idOnly(key));
    if (obj == null) {
      throw life.catalogue.api.exception.NotFoundException.keyNotFound(objClass, key);
    }
    return obj;
  }

  @PUT
  @Path("{key}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void update(@PathParam("key") Integer key, T obj, @Auth User user, @Context UriInfo uri, @Context HttpHeaders headers) {
    warn(uri, headers);
    obj.setId(key);
    obj.applyUser(user);
    int i = dao.update(obj, user.getKey());
    if (i == 0) {
      throw life.catalogue.api.exception.NotFoundException.keyNotFound(objClass, key);
    }
  }

  @DELETE
  @Path("{key}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void delete(@PathParam("key") Integer key, @Auth User user, @Context UriInfo uri, @Context HttpHeaders headers) {
    warn(uri, headers);
    int i = dao.delete(DSID.idOnly(key), user.getKey());
    if (i == 0) {
      throw NotFoundException.keyNotFound(objClass, key);
    }
  }
  
}
