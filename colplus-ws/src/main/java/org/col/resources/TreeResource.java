package org.col.resources;

import java.util.List;
import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import io.dropwizard.auth.Auth;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.*;
import org.col.dao.TaxonDao;
import org.col.db.mapper.TaxonMapper;
import org.col.db.mapper.TreeMapper;
import org.col.dw.auth.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dataset/{datasetKey}/tree")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class TreeResource {
  private static final int DEFAULT_PAGE_SIZE = 100;
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(TreeResource.class);
  
  
  @GET
  public ResultPage<TreeNode> root(@PathParam("datasetKey") int datasetKey, @Valid @BeanParam Page page, @Context SqlSession session) {
    Page p = page == null ? new Page(0, DEFAULT_PAGE_SIZE) : page;
    List<TreeNode> result = session.getMapper(TreeMapper.class).root(datasetKey, p);
    int total = result.size() == p.getLimit() ? session.getMapper(TaxonMapper.class).countRoot(datasetKey) : result.size();
    return new ResultPage<>(p, total, result);
  }
  
  @GET
  @Path("{id}")
  public List<TreeNode> parents(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    return session.getMapper(TreeMapper.class).parents(datasetKey, id);
  }
  
  @DELETE
  @Path("{id}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void deleteRecursively(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id,
                                @Auth ColUser user,
                                @Context SqlSession session) {
    TaxonDao dao = new TaxonDao(session);
    dao.deleteRecursively(new DatasetID(datasetKey, id), user);
    session.commit();
  }
  
  @GET
  @Path("{id}/children")
  public ResultPage<TreeNode> children(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id,
                                                 @Valid @BeanParam Page page, @Context SqlSession session) {
    Page p = page == null ? new Page(0, DEFAULT_PAGE_SIZE) : page;
    List<TreeNode> result = session.getMapper(TreeMapper.class).children(datasetKey, id, p);
    int total = result.size() == p.getLimit() ?
        session.getMapper(TaxonMapper.class).countChildren(datasetKey, id) : result.size();
    return new ResultPage<>(p, total, result);
  }
  
}
