package life.catalogue.resources;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DataEntity;
import life.catalogue.api.model.User;
import life.catalogue.dao.DataEntityDao;
import life.catalogue.dw.auth.Roles;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.auth.Auth;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public abstract class AbstractGlobalResource<T extends DataEntity<Integer>> {
  
  protected final Class<T> objClass;
  protected final DataEntityDao<Integer, T, ?> dao;
  protected final SqlSessionFactory factory;

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(AbstractGlobalResource.class);

  public AbstractGlobalResource(Class<T> objClass, DataEntityDao<Integer, T, ?> dao, SqlSessionFactory factory) {
    this.objClass = objClass;
    this.dao = dao;
    this.factory = factory;
  }
  
  /**
   * @return the primary key of the object. Together with the CreatedResponseFilter will return a 201 location
   */
  @POST
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public Integer create(T obj, @Auth User user) {
    if (obj != null) {
      obj.applyUser(user);
    }
    return dao.create(obj, user.getKey());
  }

  @GET
  @Path("{key}")
  public T get(@PathParam("key") Integer key) {
    T obj = dao.get(key);
    if (obj == null) {
      throw NotFoundException.notFound(objClass, key);
    }
    return obj;
  }
  
  @PUT
  @Path("{key}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void update(@PathParam("key") Integer key, T obj, @Auth User user) {
    if (obj==null) {
      throw new IllegalArgumentException("No update object given for key " + key);
    }
    obj.setKey(key);
    obj.applyUser(user);
    String msg = allowUpdate(obj, user);
    if (msg == null) {
      int i = dao.update(obj, user.getKey());
      if (i == 0) {
        throw NotFoundException.notFound(objClass, key);
      }
    } else {
      throw new IllegalArgumentException(msg);
    }
  }

  /**
   * Override this method if updates should be restricted.
   * If no update is allowed a non null message must be returned that explains why.
   * By default all updates are allowed and null is returned.
   */
  protected String allowUpdate(T obj, User user){
    return null;
  }

  @DELETE
  @Path("{key}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void delete(@PathParam("key") Integer key, @QueryParam("async") boolean async, @Auth User user) {
    int i = dao.delete(key, user.getKey());
    if (i == 0) {
      throw NotFoundException.notFound(objClass, key);
    }
  }
}
