package life.catalogue.resources;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.dao.DatasetEntityDao;
import life.catalogue.dw.auth.Roles;
import life.catalogue.dw.jersey.filter.ProjectOnly;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

@SuppressWarnings("static-method")
/**
 *
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public abstract class AbstractDatasetScopedResource<K, T extends DatasetScopedEntity<K>, R> {

  private final Class<T> objClass;
  protected final DatasetEntityDao<K, T, ?> dao;

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(AbstractDatasetScopedResource.class);

  public AbstractDatasetScopedResource(Class<T> objClass, DatasetEntityDao<K, T, ?> dao) {
    this.objClass = objClass;
    this.dao = dao;
  }
  
  @GET
  public ResultPage<T> search(@PathParam("key") int datasetKey,
                            @BeanParam R request,
                            @Valid @BeanParam Page page) {
    return searchImpl(datasetKey, request ,page);
  }

  /**
   * Default search is simply a paging through all by datasetKey.
   * Override to provide real searches
   */
  ResultPage<T> searchImpl(int datasetKey, R request, Page page) {
    return dao.list(datasetKey, page);
  }

  @GET
  @Hidden
  @Path("count")
  public int count(@PathParam("key") int datasetKey) {
    return dao.countByDataset(datasetKey);
  }

  /**
   * @return the primary key of the object. Together with the CreatedResponseFilter will return a 201 location
   */
  @POST
  @ProjectOnly
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public K create(@PathParam("key") int datasetKey, @Valid T obj, @Auth User user) {
    obj.setDatasetKey(datasetKey);
    dao.create(obj, user.getKey());
    return obj.getId();
  }
  
  /**
   * Gets entity by its key and throws NotFoundException if not existing
   */
  @GET
  @Path("{id}")
  public T get(@PathParam("key") int datasetKey, @PathParam("id") K id) {
    DSIDValue<K> key = new DSIDValue<>(datasetKey, id);
    T obj = dao.get(key);
    if (obj == null) {
      throw NotFoundException.notFound(objClass, key);
    }
    return obj;
  }
  
  @PUT
  @Path("{id}")
  @ProjectOnly
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void update(@PathParam("key") int datasetKey, @PathParam("id") K id, @Valid T obj, @Auth User user) {
    obj.setDatasetKey(datasetKey);
    obj.setId(id);
    int i = dao.update(obj, user.getKey());
    if (i == 0) {
      throw NotFoundException.notFound(objClass, new DSIDValue<>(datasetKey, id));
    }
  }

  @DELETE
  @Path("{id}")
  @ProjectOnly
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void delete(@PathParam("key") int datasetKey, @PathParam("id") K id, @Context UriInfo uri, @Auth User user) {
    DSIDValue<K> key = new DSIDValue<>(datasetKey, id);
    int i = dao.delete(key, user.getKey());
    if (i == 0) {
      throw NotFoundException.notFound(objClass, key);
    }
  }
  
}
