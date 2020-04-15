package life.catalogue.resources;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import io.dropwizard.auth.Auth;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.dao.DatasetEntityDao;
import life.catalogue.dw.auth.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public abstract class AbstractDatasetScopedResource<K, T extends DatasetScopedEntity<K>> {

  private final Class<T> objClass;
  protected final DatasetEntityDao<K, T, ?> dao;

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(AbstractDatasetScopedResource.class);

  public AbstractDatasetScopedResource(Class<T> objClass, DatasetEntityDao<K, T, ?> dao) {
    this.objClass = objClass;
    this.dao = dao;
  }
  
  @GET
  public ResultPage<T> list(@PathParam("datasetKey") int datasetKey,
                            @Valid @BeanParam Page page,
                            @Context UriInfo uri) {
    return dao.list(datasetKey, page);
  }
  
  /**
   * @return the primary key of the object. Together with the CreatedResponseFilter will return a 201 location
   */
  @POST
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public K create(@PathParam("datasetKey") int datasetKey, @Valid T obj, @Auth User user) {
    obj.setDatasetKey(datasetKey);
    dao.create(obj, user.getKey());
    return obj.getId();
  }
  
  /**
   * Gets entity by its key and throws NotFoundException if not existing
   */
  @GET
  @Path("{id}")
  public T get(@PathParam("datasetKey") int datasetKey, @PathParam("id") K id) {
    DSIDValue<K> key = new DSIDValue<>(datasetKey, id);
    T obj = dao.get(key);
    if (obj == null) {
      throw NotFoundException.idNotFound(objClass, key);
    }
    return obj;
  }
  
  @PUT
  @Path("{id}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void update(@PathParam("datasetKey") int datasetKey, @PathParam("id") K id, @Valid T obj, @Auth User user) {
    obj.setDatasetKey(datasetKey);
    obj.setId(id);
    int i = dao.update(obj, user.getKey());
    if (i == 0) {
      throw NotFoundException.idNotFound(objClass, new DSIDValue<>(datasetKey, id));
    }
  }

  @DELETE
  @Path("{id}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void delete(@PathParam("datasetKey") int datasetKey, @PathParam("id") K id, @Auth User user) {
    DSIDValue<K> key = new DSIDValue<>(datasetKey, id);
    int i = dao.delete(key, user.getKey());
    if (i == 0) {
      throw NotFoundException.idNotFound(objClass, key);
    }
  }
  
}
