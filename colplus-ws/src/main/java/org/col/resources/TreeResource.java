package org.col.resources;

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
  public ResultPage<TreeNode> root(@PathParam("datasetKey") int datasetKey, @Valid @BeanParam Page page, @Context SqlSession session) {
    Page p = page == null ? new Page(0, DEFAULT_PAGE_SIZE) : page;
    List<TreeNode> result = session.getMapper(TreeMapper.class).root(datasetKey, p);
    return new ResultPage<>(p, result, () -> session.getMapper(TaxonMapper.class).countRoot(datasetKey));
  }
  
  @GET
  @Path("{id}")
  public List<TreeNode> parents(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    return session.getMapper(TreeMapper.class).parents(datasetKey, id);
  }
  
  @DELETE
  @Path("{id}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void deleteRecursively(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id, @Auth ColUser user) {
    dao.deleteRecursively(datasetKey, id, user);
  }
  
  @GET
  @Path("{id}/children")
  public ResultPage<TreeNode> children(@PathParam("datasetKey") int datasetKey,
                                       @PathParam("id") String id,
                                       @QueryParam("insertPlaceholder") boolean insertPlaceholder,
                                                 @Valid @BeanParam Page page, @Context SqlSession session) {
    TreeMapper trm = session.getMapper(TreeMapper.class);
    TaxonMapper tm = session.getMapper(TaxonMapper.class);
    Page p = page == null ? new Page(0, DEFAULT_PAGE_SIZE) : page;
  
    RankID parent = parseID(id);
    List<TreeNode> result = trm.children(datasetKey, parent.id, parent.rank, insertPlaceholder, p);;

    Supplier<Integer> countSupplier;
    if (insertPlaceholder && !result.isEmpty()) {
      countSupplier =  () -> tm.countChildrenWithRank(datasetKey, parent.id, result.get(0).getRank());
    } else {
      countSupplier =  () -> tm.countChildren(datasetKey, parent.id);
    }

    if (insertPlaceholder && result.size() < p.getLimit()) {
      // we *might* need a placeholder, check if there are more children of other ranks
      int allChildren = tm.countChildren(datasetKey, parent.id);
      if (allChildren > result.size()) {
        TreeNode placeHolder = placeholder(result.get(0), allChildren-result.size());
        result.add(placeHolder);
      }
    }
    return new ResultPage<>(p, result, countSupplier);
  }
  
  private static TreeNode placeholder(TreeNode sibling, int childCount){
    TreeNode tn = new TreeNode();
    tn.setDatasetKey(sibling.getDatasetKey());
    tn.setSectorKey(sibling.getSectorKey());
    tn.setId(sibling.getParentId() + INC_SEDIS + sibling.getRank().name());
    tn.setRank(sibling.getRank());
    tn.setName("Not assigned");
    tn.setChildCount(childCount);
    tn.setStatus(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
    return tn;
  }
  
  static class RankID {
    String id;
    Rank rank;
  
    public RankID(String id, Rank rank) {
      this.id = id;
      this.rank = rank;
    }
  }
  
  @VisibleForTesting
  protected static RankID parseID(String id){
    Matcher m = ID_PATTERN.matcher(id);
    if (m.find()) {
      try {
        return new RankID(m.group(1), Rank.valueOf(m.group(2)));
      } catch (IllegalArgumentException e) {
        LOG.warn("Bad incertae sedis ID " + id);
      }
    }
    return new RankID(id, null);
  }
  
}
