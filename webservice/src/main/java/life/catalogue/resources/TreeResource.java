package life.catalogue.resources;

import io.dropwizard.auth.Auth;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.dao.TaxonDao;
import life.catalogue.dao.TreeDao;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.db.mapper.TreeMapper;
import life.catalogue.dw.auth.Roles;
import org.apache.ibatis.session.SqlSession;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

@Path("/dataset/{datasetKey}/tree")
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

  private static Page defaultPage(Page page){
    return page == null ? new Page(0, DEFAULT_PAGE_SIZE) : page;
  }

  @GET
  public ResultPage<TreeNode> root(@PathParam("datasetKey") int datasetKey,
                                   @QueryParam("catalogueKey") @DefaultValue(Datasets.DRAFT_COL+"") int catalogueKey,
                                   @QueryParam("type") TreeNode.Type type,
                                   @Valid @BeanParam Page page) {
    return tree.root(datasetKey, catalogueKey, type, defaultPage(page));
  }
  
  @GET
  @Path("{id}")
  public List<TreeNode> parents(@PathParam("datasetKey") int datasetKey,
                                @PathParam("id") String id,
                                @QueryParam("catalogueKey") @DefaultValue(Datasets.DRAFT_COL+"") int catalogueKey,
                                @QueryParam("insertPlaceholder") boolean placeholder,
                                @QueryParam("type") TreeNode.Type type) {
    return tree.parents(DSID.key(datasetKey, id), catalogueKey, placeholder, type);
  }

  @DELETE
  @Path("{id}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void deleteRecursively(@PathParam("datasetKey") int datasetKey,
                                @PathParam("id") String id,
                                @Auth ColUser user) {
    dao.deleteRecursively(DSID.key(datasetKey, id), user);
  }
  
  @GET
  @Path("{id}/children")
  public ResultPage<TreeNode> children(@PathParam("datasetKey") int datasetKey,
                                       @PathParam("id") String id,
                                       @QueryParam("catalogueKey") @DefaultValue(Datasets.DRAFT_COL+"") int catalogueKey,
                                       @QueryParam("insertPlaceholder") boolean placeholder,
                                       @QueryParam("type") TreeNode.Type type,
                                       @Valid @BeanParam Page page) {
    return tree.children(DSID.key(datasetKey, id), catalogueKey, placeholder, type, defaultPage(page));
  }
}
