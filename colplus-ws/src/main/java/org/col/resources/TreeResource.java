package org.col.resources;

import java.util.Collections;
import java.util.List;
import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import io.dropwizard.auth.Auth;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.*;
import org.col.db.dao.TaxonDao;
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


  /**
   * @return the primary key of the object. Together with the CreatedResponseFilter will return a 201 location
   */
  @POST
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public String create(@PathParam("datasetKey") Integer datasetKey, @Valid TreeNode obj,
                       @Auth ColUser user, @Context SqlSession session) {
    DatasetID t = new TaxonDao(session).copyTaxon(new DatasetID(obj.getDatasetKey(), obj.getId()), new DatasetID(datasetKey, obj.getParentId()), user, Collections.emptySet());
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
  public ResultPage<? extends TreeNode> root(@PathParam("datasetKey") int datasetKey, @Valid @BeanParam Page page, @Context SqlSession session) {
    Page p = page == null ? new Page(0, DEFAULT_PAGE_SIZE) : page;
    List<? extends TreeNode> result = session.getMapper(TreeMapper.class).root(datasetKey, p);
    int total = result.size() == p.getLimit() ?
        session.getMapper(TaxonMapper.class).count(datasetKey, true) : result.size();
    return new ResultPage<>(p, total, result);
  }
  
  @GET
  @Path("{id}")
  public List<? extends TreeNode> parents(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    return session.getMapper(TreeMapper.class).parents(datasetKey, id);
  }
  
  @GET
  @Path("{id}/children")
  public ResultPage<? extends TreeNode> children(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id,
                                                 @Valid @BeanParam Page page, @Context SqlSession session) {
    Page p = page == null ? new Page(0, DEFAULT_PAGE_SIZE) : page;
    List<? extends TreeNode> result = session.getMapper(TreeMapper.class).children(datasetKey, id, p);
    int total = result.size() == p.getLimit() ?
        session.getMapper(TaxonMapper.class).countChildren(datasetKey, id) : result.size();
    return new ResultPage<>(p, total, result);
  }
  
}
