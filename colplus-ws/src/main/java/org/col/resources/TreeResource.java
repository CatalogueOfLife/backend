package org.col.resources;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.auth.Auth;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.*;
import org.col.api.vocab.Datasets;
import org.col.api.vocab.TaxonomicStatus;
import org.col.dao.TaxonDao;
import org.col.db.mapper.TaxonMapper;
import org.col.db.mapper.TreeMapper;
import org.col.dw.auth.Roles;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dataset/{datasetKey}/tree")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class TreeResource {
  private static final int DEFAULT_PAGE_SIZE = 100;
  private static final String INC_SEDIS = "--incertae-sedis--";
  private static final Pattern ID_PATTERN = Pattern.compile("^(.+)"+INC_SEDIS+"([A-Z_]+)$");
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(TreeResource.class);
  private final TaxonDao dao;
  
  public TreeResource(TaxonDao dao) {
    this.dao = dao;
  }
  
  @GET
  public ResultPage<TreeNode> root(@PathParam("datasetKey") int datasetKey,
                                   @QueryParam("catalogueKey") @DefaultValue(Datasets.DRAFT_COL+"") int catalogueKey,
                                   @Valid @BeanParam Page page,
                                   @Context SqlSession session) {
    Page p = page == null ? new Page(0, DEFAULT_PAGE_SIZE) : page;
    List<TreeNode> result = session.getMapper(TreeMapper.class).root(catalogueKey, datasetKey, p);
    return new ResultPage<>(p, result, () -> session.getMapper(TaxonMapper.class).countRoot(datasetKey));
  }
  
  @GET
  @Path("{id}")
  public List<TreeNode> parents(@PathParam("datasetKey") int datasetKey,
                                @PathParam("id") String id,
                                @QueryParam("catalogueKey") @DefaultValue(Datasets.DRAFT_COL+"") int catalogueKey,
                                @Context SqlSession session) {
    RankID parent = parseID(datasetKey, id);
    TreeMapper trm = session.getMapper(TreeMapper.class);
    LinkedList<TreeNode> parents = new LinkedList<>(trm.parents(catalogueKey, parent));
    if (parent.rank != null) {
      // this was a placeholder, check how many intermediates we need
      List<Rank> sedisRanks = trm.childrenRanks(parent, parent.rank);
      sedisRanks.add(parent.rank);
      TreeNode parentNode = parents.getLast();
      for (Rank r : sedisRanks) {
        parents.addFirst(placeholder(parentNode, r, 1));
      }
    }
    return parents;
  }
  
  @DELETE
  @Path("{id}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void deleteRecursively(@PathParam("datasetKey") int datasetKey,
                                @PathParam("id") String id,
                                @Auth ColUser user) {
    dao.deleteRecursively(new DSIDValue<>(datasetKey, id), user);
  }
  
  @GET
  @Path("{id}/children")
  public ResultPage<TreeNode> children(@PathParam("datasetKey") int datasetKey,
                                       @PathParam("id") String id,
                                       @QueryParam("catalogueKey") @DefaultValue(Datasets.DRAFT_COL+"") int catalogueKey,
                                       @QueryParam("insertPlaceholder") boolean insertPlaceholder,
                                                 @Valid @BeanParam Page page, @Context SqlSession session) {
    TreeMapper trm = session.getMapper(TreeMapper.class);
    TaxonMapper tm = session.getMapper(TaxonMapper.class);
    Page p = page == null ? new Page(0, DEFAULT_PAGE_SIZE) : page;
  
    RankID parent = parseID(datasetKey, id);
    List<TreeNode> result = trm.children(catalogueKey, parent, parent.rank, insertPlaceholder, p);;

    Supplier<Integer> countSupplier;
    if (insertPlaceholder && !result.isEmpty()) {
      countSupplier =  () -> tm.countChildrenWithRank(parent, result.get(0).getRank());
    } else {
      countSupplier =  () -> tm.countChildren(parent);
    }

    if (insertPlaceholder && !result.isEmpty() && result.size() < p.getLimit()) {
      // we *might* need a placeholder, check if there are more children of other ranks
      int allChildren = tm.countChildren(parent);
      if (allChildren > result.size()) {
        TreeNode placeHolder = placeholder(result.get(0), allChildren-result.size());
        result.add(placeHolder);
      }
    }
    return new ResultPage<>(p, result, countSupplier);
  }
  
  private static TreeNode placeholder(TreeNode parent, Rank rank, int childCount){
    return placeholder(parent, parent.getId(), rank, childCount);
  }
  
  private static TreeNode placeholder(TreeNode sibling, int childCount){
    return placeholder(sibling, sibling.getParentId(), sibling.getRank(), childCount);
  }
  
  private static TreeNode placeholder(TreeNode template, String parentID, Rank rank, int childCount){
    TreeNode tn = new TreeNode();
    tn.setDatasetKey(template.getDatasetKey());
    tn.setSectorKey(template.getSectorKey());
    tn.setId(parentID + INC_SEDIS + rank.name());
    tn.setRank(rank);
    tn.setName("Not assigned");
    tn.setChildCount(childCount);
    tn.setStatus(TaxonomicStatus.ACCEPTED);
    return tn;
  }
  
  static class RankID extends DSIDValue<String> {
    Rank rank;
  
    public RankID(int datasetKey, String id, Rank rank) {
      super(datasetKey, id);
      this.rank = rank;
    }
  }
  
  @VisibleForTesting
  protected static RankID parseID(int datasetKey, String id){
    Matcher m = ID_PATTERN.matcher(id);
    if (m.find()) {
      try {
        return new RankID(datasetKey, m.group(1), Rank.valueOf(m.group(2)));
      } catch (IllegalArgumentException e) {
        LOG.warn("Bad incertae sedis ID " + id);
      }
    }
    return new RankID(datasetKey, id, null);
  }
  
}
