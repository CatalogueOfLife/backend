package org.col.resources;

import java.util.List;
import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import io.dropwizard.auth.Auth;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.ColUser;
import org.col.api.model.DatasetID;
import org.col.api.model.Taxon;
import org.col.api.model.TreeNode;
import org.col.db.dao.TaxonDao;
import org.col.db.mapper.TreeMapper;
import org.col.dw.auth.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dataset/{datasetKey}/tree")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class TreeResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(TreeResource.class);


  /**
   * @return the primary key of the object. Together with the CreatedResponseFilter will return a 201 location
   */
  @POST
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public String create(@PathParam("datasetKey") Integer datasetKey, @Valid TreeNode obj,
                       @Auth ColUser user, @Context SqlSession session) {
    Taxon t = new TaxonDao(session).copy(new DatasetID(obj.getDatasetKey(), obj.getId()), new DatasetID(datasetKey, obj.getParentId()), user);
    return t.getId();
  }


  @PUT
  @Path("{id}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void update(@PathParam("datasetKey") Integer datasetKey, @PathParam("id") String id, TreeNode obj,
                     @Auth ColUser user, @Context SqlSession session) {
    obj.setDatasetKey(datasetKey);
    obj.setId(id);
    //TODO...
    throw new NotImplementedException("update not implemented yet");
    //session.commit();
  }

  @DELETE
  @Path("{id}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void delete(@PathParam("datasetKey") Integer datasetKey, @PathParam("id") String id,
                     @Auth ColUser user, @Context SqlSession session) {
    throw new NotImplementedException("delete not implemented yet");
    //session.commit();
  }

  @GET
  public List<TreeNode.TreeNodeMybatis> root(@PathParam("datasetKey") int datasetKey, @Context SqlSession session) {
    return session.getMapper(TreeMapper.class).root(datasetKey);
  }
  
  @GET
  @Path("{id}")
  public List<TreeNode.TreeNodeMybatis> parents(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    return session.getMapper(TreeMapper.class).parents(datasetKey, id);
  }
  
  @GET
  @Path("{id}/children")
  public List<TreeNode.TreeNodeMybatis> children(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    return session.getMapper(TreeMapper.class).children(datasetKey, id);
  }
  
}
