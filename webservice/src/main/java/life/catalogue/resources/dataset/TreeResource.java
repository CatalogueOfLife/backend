package life.catalogue.resources.dataset;

import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.dao.TaxonDao;
import life.catalogue.dao.TreeDao;
import life.catalogue.dw.auth.Roles;
import life.catalogue.dw.jersey.filter.ProjectOnly;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.auth.Auth;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/dataset/{key}/tree")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class TreeResource {
  private static final int DEFAULT_PAGE_SIZE = 100;

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(TreeResource.class);
  private final TaxonDao dao;
  private final TreeDao tree;
  
  public TreeResource(TaxonDao dao, TreeDao tree) {
    this.dao = dao;
    this.tree = tree;
  }

  private static Page page(Integer limit, Integer offset){
    Page p = new Page(ObjectUtils.coalesce(offset, 0), DEFAULT_PAGE_SIZE);
    // we set the limit here and not in the constructor to bypass the max argument exception
    // we want to allow higher limits than 1000 for the tree API only !!!
    p.setLimit(ObjectUtils.coalesce(limit, DEFAULT_PAGE_SIZE));
    return p;
  }

  @GET
  public ResultPage<TreeNode> root(@PathParam("key") int datasetKey,
                                   @QueryParam("catalogueKey") @DefaultValue(Datasets.COL +"") int catalogueKey,
                                   @QueryParam("type") TreeNode.Type type,
                                   @QueryParam("extinct") @DefaultValue("true") boolean inclExtinct,
                                   @QueryParam("insertPlaceholder") boolean placeholder,
                                   @QueryParam("limit") Integer limit,
                                   @QueryParam("offset") Integer offset) {
    return tree.root(datasetKey, catalogueKey, placeholder, inclExtinct, type, page(limit, offset));
  }
  
  @GET
  @Path("{id}")
  public List<TreeNode> classification(@PathParam("key") int datasetKey,
                                @PathParam("id") String id,
                                @QueryParam("catalogueKey") @DefaultValue(Datasets.COL +"") int catalogueKey,
                                @QueryParam("extinct") @DefaultValue("true") boolean inclExtinct,
                                @QueryParam("insertPlaceholder") boolean placeholder,
                                @QueryParam("type") TreeNode.Type type) {
    return tree.classification(DSID.of(datasetKey, id), catalogueKey, inclExtinct, placeholder, type);
  }

  @DELETE
  @Path("{id}")
  @ProjectOnly
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void deleteRecursively(@PathParam("key") int datasetKey,
                                @PathParam("id") String id,
                                @Auth User user) {
    dao.deleteRecursively(DSID.of(datasetKey, id), false, user);
  }
  
  @GET
  @Path("{id}/children")
  public ResultPage<TreeNode> children(@PathParam("key") int datasetKey,
                                       @PathParam("id") String id,
                                       @QueryParam("catalogueKey") @DefaultValue(Datasets.COL +"") int catalogueKey,
                                       @QueryParam("insertPlaceholder") boolean placeholder,
                                       @QueryParam("extinct") @DefaultValue("true") boolean inclExtinct,
                                       @QueryParam("type") TreeNode.Type type,
                                       @QueryParam("limit") Integer limit,
                                       @QueryParam("offset") Integer offset) {
    return tree.children(DSID.of(datasetKey, id), catalogueKey, placeholder, inclExtinct, type, page(limit, offset));
  }
}
