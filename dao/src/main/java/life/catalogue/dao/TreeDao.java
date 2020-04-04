package life.catalogue.dao;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.db.mapper.TreeMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

public class TreeDao {
  private static final Logger LOG = LoggerFactory.getLogger(TreeDao.class);
  private final SqlSessionFactory factory;

  public TreeDao(SqlSessionFactory factory) {
    this.factory = factory;
  }

  public ResultPage<TreeNode> root(int datasetKey, int catalogueKey, TreeNode.Type type, Page page) {
    try (SqlSession session = factory.openSession()){
      List<TreeNode> result = session.getMapper(TreeMapper.class).root(catalogueKey, type, datasetKey, page);
      return new ResultPage<>(page, result, () -> session.getMapper(TaxonMapper.class).countRoot(datasetKey));
    }
  }

  /**
   * @return classification starting with the given start id
   */
  public List<TreeNode> classification(DSID<String> id, int catalogueKey, boolean placeholder, TreeNode.Type type) {
    RankID key = RankID.parseID(id);
    try (SqlSession session = factory.openSession()){
      TreeMapper trm = session.getMapper(TreeMapper.class);

      Rank pRank = null;
      LinkedList<TreeNode> parents = new LinkedList<>();
      if (key.rank != null) {
        placeholder = true;
        pRank = key.rank;
        TreeNode parentNode = trm.get(catalogueKey, type, key);
        parents.add(placeholder(parentNode, key.rank, 1));
        parents.addAll(parentPlaceholder(trm, parentNode, pRank));
        //parents.add(parentNode);
        pRank = parentNode.getRank();
      }
      for (TreeNode tn : trm.classification(catalogueKey, type, key)) {
        if (placeholder) {
          parents.addAll(parentPlaceholder(trm, tn, pRank));
        }
        parents.add(tn);
        pRank = tn.getRank();
      }
      return parents;
    }
  }

  private static List<TreeNode> parentPlaceholder(TreeMapper trm, TreeNode tn, @Nullable Rank exclRank){
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

  public ResultPage<TreeNode> children(DSID<String> id, int catalogueKey, boolean placeholder, TreeNode.Type type, Page page) {
    try (SqlSession session = factory.openSession()){
      TreeMapper trm = session.getMapper(TreeMapper.class);
      TaxonMapper tm = session.getMapper(TaxonMapper.class);

      RankID parent = RankID.parseID(id);
      List<TreeNode> result = placeholder ?
        trm.childrenWithPlaceholder(catalogueKey, type, parent, parent.rank, page) :
        trm.children(catalogueKey, type, parent, parent.rank, page);

      Supplier<Integer> countSupplier;
      if (placeholder && !result.isEmpty()) {
        countSupplier =  () -> tm.countChildrenWithRank(parent, result.get(0).getRank());
      } else {
        countSupplier =  () -> tm.countChildren(parent);
      }

      if (placeholder && !result.isEmpty() && result.size() < page.getLimit()) {
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
      return new ResultPage<>(page, result, countSupplier);
    }
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
