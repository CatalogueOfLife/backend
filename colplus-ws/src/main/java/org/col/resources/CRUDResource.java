package org.col.resources;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.col.api.exception.NotFoundException;
import org.col.api.model.IntKey;
import org.col.db.mapper.CRUDMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public abstract class CRUDResource<T extends IntKey> {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(CRUDResource.class);
  private final Class<T> objClass;
  private final Class<? extends CRUDMapper<T>> mapperClass;
  
  public CRUDResource(Class<T> objClass, Class<? extends CRUDMapper<T>> mapperClass) {
    this.objClass = objClass;
    this.mapperClass = mapperClass;
  }
  
  
  /**
   * @return the primary key of the object. Together with the CreatedResponseFilter will return a 201 location
   */
  @POST
  public Integer create(@Valid T obj, @Context SqlSession session) {
    session.getMapper(mapperClass).create(obj);
    session.commit();
    return obj.getKey();
  }

  @GET
  @Path("{key}")
  public T get(@PathParam("key") int key, @Context SqlSession session) {
    return session.getMapper(mapperClass).get(key);
  }

  @PUT
  @Path("{key}")
  public void update(T obj, @Context SqlSession session) {
    int i = session.getMapper(mapperClass).update(obj);
    if (i == 0) {
      throw NotFoundException.keyNotFound(objClass, obj.getKey());
    }
    session.commit();
  }

  @DELETE
  @Path("{key}")
  public void delete(@PathParam("key") Integer key, @Context SqlSession session) {
    int i = session.getMapper(mapperClass).delete(key);
    if (i == 0) {
      throw NotFoundException.keyNotFound(objClass, key);
    }
    session.commit();
  }

}
