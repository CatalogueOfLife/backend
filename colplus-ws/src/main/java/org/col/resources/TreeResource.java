package org.col.resources;

import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.TreeNode;
import org.col.db.mapper.TreeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dataset/{datasetKey}/tree")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class TreeResource {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(TreeResource.class);
  
  @GET
  public List<TreeNode> root(@PathParam("datasetKey") int datasetKey, @Context SqlSession session) {
    return session.getMapper(TreeMapper.class).root(datasetKey);
  }
  
  @GET
  @Path("{id}")
  public List<TreeNode> parents(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    return session.getMapper(TreeMapper.class).parents(datasetKey, id);
  }
  
  @GET
  @Path("{id}/children")
  public List<TreeNode> children(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    return session.getMapper(TreeMapper.class).children(datasetKey, id);
  }
  
}
