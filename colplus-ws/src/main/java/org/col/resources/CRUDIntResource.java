package org.col.resources;

import io.dropwizard.auth.Auth;
import org.apache.ibatis.session.SqlSession;
import org.col.api.exception.NotFoundException;
import org.col.api.model.ColUser;
import org.col.api.model.IntKey;
import org.col.db.mapper.CRUDIntMapper;
import org.col.dw.auth.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public abstract class CRUDIntResource<T extends IntKey> {

  private final Class<T> objClass;
  private final Class<? extends CRUDIntMapper<T>> mapperClass;

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(CRUDIntResource.class);

  public CRUDIntResource(Class<T> objClass, Class<? extends CRUDIntMapper<T>> mapperClass) {
    this.objClass = objClass;
    this.mapperClass = mapperClass;
  }

  /**
   * @return the primary key of the object. Together with the CreatedResponseFilter will return a 201 location
   */
  @POST
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public Integer create(@Valid T obj, @Auth ColUser user, @Context SqlSession session) {
    session.getMapper(mapperClass).create(obj);
    session.commit();
    return obj.getKey();
  }

  @GET
  @Path("{key}")
  public T get(@PathParam("key") Integer key, @Context SqlSession session) {
    return session.getMapper(mapperClass).get(key);
  }

  @PUT
  @Path("{key}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void update(@PathParam("key") Integer key, T obj, @Auth ColUser user, @Context SqlSession session) {
    obj.setKey(key);
    int i = session.getMapper(mapperClass).update(obj);
    if (i == 0) {
      throw org.col.api.exception.NotFoundException.keyNotFound(objClass, key);
    }
    session.commit();
  }

  @DELETE
  @Path("{key}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void delete(@PathParam("key") Integer key, @Auth ColUser user, @Context SqlSession session) {
    int i = session.getMapper(mapperClass).delete(key);
    if (i == 0) {
      throw NotFoundException.keyNotFound(objClass, key);
    }
    session.commit();
  }
}
