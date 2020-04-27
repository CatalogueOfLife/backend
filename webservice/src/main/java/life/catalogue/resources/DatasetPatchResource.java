package life.catalogue.resources;

import io.dropwizard.auth.Auth;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.db.mapper.DatasetPatchMapper;
import life.catalogue.dw.auth.Roles;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@Path("/dataset/{datasetKey}/patch")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class DatasetPatchResource {

  private static final Logger LOG = LoggerFactory.getLogger(DatasetPatchResource.class);


  /**
   * @return the primary key of the object. Together with the CreatedResponseFilter will return a 201 location
   */
  @POST
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public DSID<Integer> create(@PathParam("datasetKey") int datasetKey, Dataset obj, @Auth User user, @Context SqlSession session) {
    obj.applyUser(user);
    session.getMapper(DatasetPatchMapper.class).create(datasetKey, obj);
    return DSID.of(datasetKey, obj.getKey());
  }

  /**
   * Gets entity by its key and throws NotFoundException if not existing
   */
  @GET
  @Path("{id}")
  public Dataset get(@PathParam("datasetKey") int datasetKey, @PathParam("id") Integer id, @Context SqlSession session) {
    return session.getMapper(DatasetPatchMapper.class).get(datasetKey, id);
  }

  @PUT
  @Path("{id}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void update(@PathParam("datasetKey") int datasetKey, @PathParam("id") Integer id, @Valid Dataset obj, @Auth User user, @Context SqlSession session) {
    obj.setKey(id);
    obj.applyUser(user);
    int i = session.getMapper(DatasetPatchMapper.class).update(datasetKey, obj);
    if (i == 0) {
      throw NotFoundException.notFound(DatasetPatch.class, new DSIDValue<>(datasetKey, id));
    }
  }

  @DELETE
  @Path("{id}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void delete(@PathParam("datasetKey") int datasetKey, @PathParam("id") Integer id, @Auth User user, @Context SqlSession session) {
    int i = session.getMapper(DatasetPatchMapper.class).delete(datasetKey, id);
    if (i == 0) {
      throw NotFoundException.notFound(DatasetPatch.class, new DSIDValue<>(datasetKey, id));
    }
  }

}
