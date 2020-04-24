package life.catalogue.resources;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import io.dropwizard.auth.Auth;
import org.apache.ibatis.session.SqlSessionFactory;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.User;
import life.catalogue.api.model.DataEntity;
import life.catalogue.dao.DataEntityDao;
import life.catalogue.dw.auth.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  public Integer create(@Valid T obj, @Auth User user) {
    obj.applyUser(user);
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
    obj.setKey(key);
    obj.applyUser(user);
    int i = dao.update(obj, user.getKey());
    if (i == 0) {
      throw NotFoundException.notFound(objClass, key);
    }
  }

  @DELETE
  @Path("{key}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void delete(@PathParam("key") Integer key, @Auth User user) {
    int i = dao.delete(key, user.getKey());
    if (i == 0) {
      throw NotFoundException.notFound(objClass, key);
    }
  }
}
