package life.catalogue.resources;

import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.auth.Auth;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.dao.TaxonDao;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Path("/dataset/{datasetKey}/tree")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class TreeResource {
  private static final int DEFAULT_PAGE_SIZE = 100;

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(TreeResource.class);
  private final TaxonDao dao;
  
  public TreeResource(TaxonDao dao) {
    this.dao = dao;
  }
  
  @GET
  public ResultPage<TreeNode> root(@PathParam("datasetKey") int datasetKey,
                                   @QueryParam("catalogueKey") @DefaultValue(Datasets.DRAFT_COL+"") int catalogueKey,
                                   @QueryParam("type") TreeNode.Type type,
                                   @Valid @BeanParam Page page,
                                   @Context SqlSession session) {
    Page p = page == null ? new Page(0, DEFAULT_PAGE_SIZE) : page;
    List<TreeNode> result = session.getMapper(TreeMapper.class).root(catalogueKey, type, datasetKey, p);
    return new ResultPage<>(p, result, () -> session.getMapper(TaxonMapper.class).countRoot(datasetKey));
  }
  
  @GET
  @Path("{id}")
  public List<TreeNode> parents(@PathParam("datasetKey") int datasetKey,
                                @PathParam("id") String id,
                                @QueryParam("catalogueKey") @DefaultValue(Datasets.DRAFT_COL+"") int catalogueKey,
                                @QueryParam("insertPlaceholder") boolean placeholder,
                                @QueryParam("type") TreeNode.Type type,
                                @Context SqlSession session) {
    RankID key = RankID.parseID(datasetKey, id);
    TreeMapper trm = session.getMapper(TreeMapper.class);

    LinkedList<TreeNode> parents = new LinkedList<>();
    if (key.rank != null) {
      TreeNode parentNode = trm.get(catalogueKey, type, key);
      parents.addAll(buildPlaceholder(trm, parentNode, key.rank));
      parents.add(parentNode);
    }
    for (TreeNode tn : trm.parents(catalogueKey, type, key)) {
      parents.addAll(buildPlaceholder(trm, tn, null));
      parents.add(tn);
    }
    return parents;
  }

  private static List<TreeNode> buildPlaceholder(TreeMapper trm, TreeNode tn, @Nullable Rank exclRank){
    List<TreeNode> nodes = new ArrayList<>();
    // ranks ordered from kingdom to lower
    List<Rank> ranks = trm.childrenRanks(tn, exclRank);
    if (ranks.size() > 1) {
      Collections.reverse(ranks);
      // we dont want no placeholder for the lowest rank
      ranks.remove(0);
      for (Rank r : ranks) {
        nodes.add(placeholder(tn, r, 1));
      }
    }
    return nodes;
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
                                       @QueryParam("insertPlaceholder") boolean placeholder,
                                       @QueryParam("type") TreeNode.Type type,
                                       @Valid @BeanParam Page page, @Context SqlSession session) {
    TreeMapper trm = session.getMapper(TreeMapper.class);
    TaxonMapper tm = session.getMapper(TaxonMapper.class);
    Page p = page == null ? new Page(0, DEFAULT_PAGE_SIZE) : page;
  
    RankID parent = RankID.parseID(datasetKey, id);
    List<TreeNode> result = placeholder ?
        trm.childrenWithPlaceholder(catalogueKey, type, parent, parent.rank, p) :
        trm.children(catalogueKey, type, parent, parent.rank, p);

    Supplier<Integer> countSupplier;
    if (placeholder && !result.isEmpty()) {
      countSupplier =  () -> tm.countChildrenWithRank(parent, result.get(0).getRank());
    } else {
      countSupplier =  () -> tm.countChildren(parent);
    }

    if (placeholder && !result.isEmpty() && result.size() < p.getLimit()) {
      // we *might* need a placeholder, check if there are more children of other ranks
      // look for the current rank of the result set
      TreeNode firstResult = result.get(0);
      int lowerChildren = tm.countChildrenBelowRank(parent, firstResult.getRank());
      if (lowerChildren > 0) {
        TreeNode tnParent = trm.get(catalogueKey, type, parent);
        TreeNode placeHolder = placeholder(tnParent, firstResult, lowerChildren);
        // does a placeholder sector exist with a matching placeholder rank?
        if (type == TreeNode.Type.SOURCE) {
          SectorMapper sm = session.getMapper(SectorMapper.class);
          Sector s = sm.getBySubject(catalogueKey, parent);
          if (s != null && s.getPlaceholderRank() == placeHolder.getRank()) {
            placeHolder.setSectorKey(s.getKey());
          }
        }
        result.add(placeHolder);
      }
    }
    return new ResultPage<>(p, result, countSupplier);
  }

  private static TreeNode placeholder(TreeNode parent, Rank rank, int childCount){
    return placeholder(parent.getDatasetKey(), parent.getSectorKey(), parent.getId(), rank, childCount);
  }
  
  private static TreeNode placeholder(TreeNode parent, TreeNode sibling, int childCount){
    return placeholder(sibling.getDatasetKey(), parent.getSectorKey(), sibling.getParentId(), sibling.getRank(), childCount);
  }
  
  private static TreeNode placeholder(Integer datasetKey, Integer sectorKey, String parentID, Rank rank, int childCount){
    TreeNode tn = new TreeNode.PlaceholderNode();
    tn.setDatasetKey(datasetKey);
    tn.setSectorKey(sectorKey);
    tn.setId(RankID.buildID(parentID, rank));
    tn.setRank(rank);
    tn.setChildCount(childCount);
    tn.setStatus(TaxonomicStatus.ACCEPTED);
    return tn;
  }

}
