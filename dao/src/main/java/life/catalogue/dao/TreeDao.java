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
import java.util.*;
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
  public List<TreeNode> classification(DSID<String> id, int projectKey, boolean placeholder, TreeNode.Type type) {
    RankID key = RankID.parseID(id);
    try (SqlSession session = factory.openSession()){
      TreeMapper trm = session.getMapper(TreeMapper.class);

      Rank pRank = null;
      LinkedList<TreeNode> classification = new LinkedList<>();
      if (key.rank != null) {
        placeholder = true;
        pRank = key.rank;
        TreeNode parentNode = trm.get(projectKey, type, key);
        classification.add(placeholder(parentNode, key.rank));
        classification.addAll(parentPlaceholder(trm, parentNode, pRank));
        pRank = parentNode.getRank();
      }
      for (TreeNode tn : trm.classification(projectKey, type, key)) {
        if (placeholder) {
          classification.addAll(parentPlaceholder(trm, tn, pRank));
        }
        classification.add(tn);
        pRank = tn.getRank();
      }
      addPlaceholderSectors(projectKey, classification, type, true, session);
      return classification;
    }
  }

  private void addPlaceholderSectors(int projectKey, List<TreeNode> nodes, TreeNode.Type type, boolean isClassification, SqlSession session) {
    // no sectors for no type
    if (type == null) return;

    SectorMapper sm = session.getMapper(SectorMapper.class);
    TreeMapper tm = session.getMapper(TreeMapper.class);
    final Map<String, Sector> sectors = new HashMap<>(); // for Type.SOURCE
    final Map<String, Integer> sectorKey = new HashMap<>(); // for Type.CATALOGUE, key on real id - placeholder of different ranks often share the same real id
    for (TreeNode n : nodes) {
      RankID key = RankID.parseID(n);
      // only check placeholders that have no sector yet
      if (key.rank == null || n.getSectorKey()!=null) continue;

      if (type == TreeNode.Type.SOURCE) {
        // load sector only once if id is the same
        if (!sectors.containsKey(key.getId())) {
          sectors.put(key.getId(), sm.getBySubject(projectKey, key));
        }
        if (sectors.get(key.getId()) != null) {
          Sector s = sectors.get(key.getId());
          if (s.getPlaceholderRank() == key.rank) {
            n.setSectorKey(s.getKey());
          }
        }
      } else if (type == TreeNode.Type.CATALOGUE) {
        // look at all sectors of children - if they are all the same the placeholder also belongs to them
        Integer secKey = null;
        if (sectorKey.containsKey(key.getId())) {
          secKey = sectorKey.get(key.getId());
        } else {
          List<Integer> secKeys = tm.childrenSectors(n);
          if (secKeys.size() == 1) {
            secKey = secKeys.get(0);
            sectorKey.put(key.getId(), secKey);
          } else {
            sectorKey.put(key.getId(), null);
          }
        }
        n.setSectorKey(secKey);
      }
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
        nodes.add(placeholder(tn, r));
      }
    }
    return nodes;
  }

  public ResultPage<TreeNode> children(DSID<String> id, int projectKey, boolean placeholder, TreeNode.Type type, Page page) {
    try (SqlSession session = factory.openSession()){
      TreeMapper trm = session.getMapper(TreeMapper.class);
      TaxonMapper tm = session.getMapper(TaxonMapper.class);

      RankID parent = RankID.parseID(id);
      List<TreeNode> result = placeholder ?
        trm.childrenWithPlaceholder(projectKey, type, parent, parent.rank, page) :
        trm.children(projectKey, type, parent, parent.rank, page);

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
          TreeNode tnParent = trm.get(projectKey, type, parent);
          TreeNode placeHolder = placeholder(tnParent, firstResult, lowerChildren);
          // does a placeholder sector exist with a matching placeholder rank?
          if (type == TreeNode.Type.SOURCE) {
            SectorMapper sm = session.getMapper(SectorMapper.class);
            Sector s = sm.getBySubject(projectKey, parent);
            if (s != null && s.getPlaceholderRank() == placeHolder.getRank()) {
              placeHolder.setSectorKey(s.getKey());
            }
          }
          result.add(placeHolder);
        }
      }
      addPlaceholderSectors(projectKey, result, type, false, session);
      return new ResultPage<>(page, result, countSupplier);
    }
  }

  private static TreeNode placeholder(TreeNode parent, Rank rank){
    return placeholder(parent.getDatasetKey(), parent.getSectorKey(), parent.getId(), rank, 1);
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
