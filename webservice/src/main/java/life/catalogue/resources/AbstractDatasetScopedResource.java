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
public abstract class AbstractDatasetScopedResource<T extends DatasetScopedEntity<String>> {

  private final Class<T> objClass;
  protected final DatasetEntityDao<String, T, ?> dao;

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(AbstractDatasetScopedResource.class);

  public AbstractDatasetScopedResource(Class<T> objClass, DatasetEntityDao<String, T, ?> dao) {
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
  public String create(@PathParam("datasetKey") int datasetKey, @Valid T obj, @Auth User user) {
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
    T obj = dao.get(new DSIDValue<>(datasetKey, id));
    if (obj == null) {
      throw NotFoundException.idNotFound(objClass, datasetKey, id);
    }
    return obj;
  }
  
  @PUT
  @Path("{id}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void update(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @Valid T obj, @Auth User user) {
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
  public void delete(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @Auth User user) {
    int i = dao.delete(new DSIDValue(datasetKey, id), user.getKey());
    if (i == 0) {
      throw NotFoundException.idNotFound(objClass, datasetKey, id);
    }
  }
  
}
