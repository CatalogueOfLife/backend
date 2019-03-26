package org.col.resources;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import io.dropwizard.auth.Auth;
import org.col.api.exception.NotFoundException;
import org.col.api.model.ColUser;
import org.col.api.model.IntKey;
import org.col.api.model.UserManaged;
import org.col.db.CRUDInt;
import org.col.dw.auth.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public abstract class CRUDIntResource<T extends IntKey & UserManaged> {

  private final Class<T> objClass;
  protected final CRUDInt<T> crud;

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(CRUDIntResource.class);

  public CRUDIntResource(Class<T> objClass, CRUDInt<T> crud) {
    this.objClass = objClass;
    this.crud = crud;
  }

  /**
   * @return the primary key of the object. Together with the CreatedResponseFilter will return a 201 location
   */
  @POST
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public Integer create(@Valid T obj, @Auth ColUser user) {
    obj.applyUser(user);
    crud.create(obj);
    return obj.getKey();
  }

  @GET
  @Path("{key}")
  public T get(@PathParam("key") Integer key) {
    return crud.get(key);
  }
  
  /**
   * Gets entity by its key and throws NotFoundException if not existing
   */
  public T getNonNull(Integer key) {
    T obj = get(key);
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
    obj.applyUser(user);
    int i = crud.update(obj);
    if (i == 0) {
      throw NotFoundException.keyNotFound(objClass, key);
    }
  }

  @DELETE
  @Path("{key}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void delete(@PathParam("key") Integer key, @Auth ColUser user) {
    int i = crud.delete(key);
    if (i == 0) {
      throw NotFoundException.keyNotFound(objClass, key);
    }
  }
}
