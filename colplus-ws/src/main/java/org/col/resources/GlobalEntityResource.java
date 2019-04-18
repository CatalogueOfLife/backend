package org.col.resources;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import io.dropwizard.auth.Auth;
import org.apache.ibatis.session.SqlSession;
import org.col.api.exception.NotFoundException;
import org.col.api.model.*;
import org.col.dao.GlobalEntityDao;
import org.col.dw.auth.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public abstract class GlobalEntityResource<T extends GlobalEntity & UserManaged> {

  private final Class<T> objClass;
  protected final GlobalEntityDao<T, ?> dao;

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(GlobalEntityResource.class);

  public GlobalEntityResource(Class<T> objClass, GlobalEntityDao<T, ?> dao) {
    this.objClass = objClass;
    this.dao = dao;
  }
  
  @GET
  public ResultPage<T> list(@Valid @BeanParam Page page, @Context SqlSession session) {
    return dao.list(page);
  }
  
  /**
   * @return the primary key of the object. Together with the CreatedResponseFilter will return a 201 location
   */
  @POST
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public Integer create(@Valid T obj, @Auth ColUser user) {
    obj.applyUser(user);
    return dao.create(obj, user.getKey());
  }

  @GET
  @Path("{key}")
  public T get(@PathParam("key") Integer key) {
    T obj = dao.get(key);
    if (obj == null) {
      throw NotFoundException.keyNotFound(objClass, key);
    }
    return obj;
  }
  
  @PUT
  @Path("{key}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void update(@PathParam("key") Integer key, T obj, @Auth ColUser user) {
    obj.setKey(key);
    int i = dao.update(obj, user.getKey());
    if (i == 0) {
      throw NotFoundException.keyNotFound(objClass, key);
    }
  }

  @DELETE
  @Path("{key}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void delete(@PathParam("key") Integer key, @Auth ColUser user) {
    int i = dao.delete(key, user.getKey());
    if (i == 0) {
      throw NotFoundException.keyNotFound(objClass, key);
    }
  }
}
