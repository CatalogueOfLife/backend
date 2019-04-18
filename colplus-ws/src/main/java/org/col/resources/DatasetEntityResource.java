package org.col.resources;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import io.dropwizard.auth.Auth;
import org.col.api.exception.NotFoundException;
import org.col.api.model.ColUser;
import org.col.api.model.DatasetEntity;
import org.col.api.model.UserManaged;
import org.col.dao.DatasetEntityDao;
import org.col.dw.auth.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public abstract class DatasetEntityResource<T extends DatasetEntity & UserManaged> {

  private final Class<T> objClass;
  protected final DatasetEntityDao<T, ?> dao;

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DatasetEntityResource.class);

  public DatasetEntityResource(Class<T> objClass, DatasetEntityDao<T, ?> dao) {
    this.objClass = objClass;
    this.dao = dao;
  }

  /**
   * @return the primary key of the object. Together with the CreatedResponseFilter will return a 201 location
   */
  @POST
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public String create(@PathParam("datasetKey") int datasetKey, @Valid T obj, @Auth ColUser user) {
    obj.setDatasetKey(datasetKey);
    dao.create(obj, user.getKey());
    return obj.getId();
  }
  
  /**
   * Gets entity by its key and throws NotFoundException if not existing
   */
  @GET
  @Path("{id}")
  public T get(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id) {
    T obj = dao.get(datasetKey, id);
    if (obj == null) {
      throw NotFoundException.idNotFound(objClass, datasetKey, id);
    }
    return obj;
  }
  
  @PUT
  @Path("{id}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void update(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @Valid T obj, @Auth ColUser user) {
    obj.setDatasetKey(datasetKey);
    obj.setId(id);
    int i = dao.update(obj, user.getKey());
    if (i == 0) {
      throw NotFoundException.idNotFound(objClass, datasetKey, id);
    }
  }

  @DELETE
  @Path("{id}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void delete(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @Auth ColUser user) {
    int i = dao.delete(datasetKey, id, user.getKey());
    if (i == 0) {
      throw NotFoundException.idNotFound(objClass, datasetKey, id);
    }
  }
  
}
