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
        // the requested key is a placeholder node itself. First get the "real" parent
        TreeNode parentNode = trm.get(projectKey, type, key);
        // first add a node for the given key - we dont know if the parent is also a placeholder yet. this can be changed later
        TreeNode thisPlaceholder = placeholder(parentNode, null, key.rank);
        classification.add(thisPlaceholder);
        classification.addAll(parentPlaceholder(trm, parentNode, key.rank));
        if (classification.size() > 1) {
          // update the parentID to point to the parent placeholder instead
          thisPlaceholder.setParentId(classification.get(1).getId());
        }
        pRank = parentNode.getRank();
      }
      boolean first = true;
      for (TreeNode tn : trm.classification(projectKey, type, key)) {
        if (first) {
          first = false;
        } else if (placeholder) {
          List<TreeNode> placeholders = parentPlaceholder(trm, tn, pRank);
          // we need to change the parentID of the last entry to point to the first placeholder
          if (!classification.isEmpty() && !placeholders.isEmpty()) {
            classification.getLast().setParentId(placeholders.get(0).getId());
          }
          classification.addAll(placeholders);
        }
        classification.add(tn);
        pRank = tn.getRank();
      }
      addPlaceholderSectors(projectKey, classification, type, session);
      updateSectorRootFlags(classification);
      return classification;
    }
  }

  private void addPlaceholderSectors(int projectKey, List<TreeNode> nodes, TreeNode.Type type, SqlSession session) {
    // no sectors for no type
    if (type == null) return;

    SectorMapper sm = session.getMapper(SectorMapper.class);
    TreeMapper tm = session.getMapper(TreeMapper.class);
    final Map<String, Sector> sectors = new HashMap<>(); // for Type.SOURCE
    for (TreeNode n : nodes) {
      RankID key = RankID.parseID(n);
      // only check placeholders that have no sector yet
      if (key.rank == null || n.getSectorKey() != null) continue;

      if (type == TreeNode.Type.SOURCE) {
        // load sector only once if id is the same
        if (!sectors.containsKey(key.getId())) {
          sectors.put(key.getId(), sm.getBySubject(projectKey, key));
        }
        if (sectors.get(key.getId()) != null) {
          Sector s = sectors.get(key.getId());
          if (s.getPlaceholderRank() == key.rank) {
            n.setSectorKey(s.getId());
          }
        }
      } else if (type == TreeNode.Type.CATALOGUE) {
        // look at all sectors of children - if they are all the same the placeholder also belongs to them
        List<Integer> secKeys = tm.childrenSectors(key, key.rank);
        if (secKeys.size() == 1) {
          n.setSectorKey(secKeys.get(0));
        }
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
      Rank child = null;
      for (Rank r : ranks) {
        if (child != null) {
          nodes.add(placeholder(tn, r, child));
        }
        child = r;
      }
      // now we miss the last one which should have no parent
      nodes.add(placeholder(tn, null, child));
    }
    return nodes;
  }

  public ResultPage<TreeNode> children(final DSID<String> id, final int projectKey, final boolean placeholder, final TreeNode.Type type, final Page page) {
    try (SqlSession session = factory.openSession()){
      TreeMapper trm = session.getMapper(TreeMapper.class);
      TaxonMapper tm = session.getMapper(TaxonMapper.class);

      final RankID parent = RankID.parseID(id);
      final TreeNode tnParent = trm.get(projectKey, type, parent);
      List<TreeNode> result = placeholder ?
        trm.childrenWithPlaceholder(projectKey, type, parent, parent.rank, page) :
        trm.children(projectKey, type, parent, parent.rank, page);
      Supplier<Integer> countSupplier;
      if (placeholder && !result.isEmpty()) {
        countSupplier =  () -> tm.countChildrenWithRank(parent, result.get(0).getRank());
      } else {
        countSupplier =  () -> tm.countChildren(parent);
      }

      if (placeholder && !result.isEmpty() && result.size() <= page.getLimit()) {
        // we *might* need a placeholder, check if there are more children of other ranks
        // look for the highest rank of the result set in the first record - they are ordered by rank!
        TreeNode firstResult = result.get(0);
        int lowerChildren = tm.countChildrenBelowRank(parent, firstResult.getRank());
        if (lowerChildren > 0) {
          List<Rank> placeholderParentRanks = trm.childrenRanks(parent, firstResult.getRank());
          TreeNode placeHolder = placeholder(tnParent, firstResult, lowerChildren, placeholderParentRanks);
          // does a placeholder sector exist with a matching placeholder rank?
          if (type == TreeNode.Type.SOURCE) {
            SectorMapper sm = session.getMapper(SectorMapper.class);
            Sector s = sm.getBySubject(projectKey, parent);
            if (s != null && s.getPlaceholderRank() == placeHolder.getRank()) {
              placeHolder.setSectorKey(s.getId());
            }
          }
          result.add(placeHolder);
        }
      }
      // update parentID to use original input
      result.forEach(c -> c.setParentId(id.getId()));
      addPlaceholderSectors(projectKey, result, type, session);
      updateSectorRootFlags(tnParent.getSectorKey(), result);
      return new ResultPage<>(page, result, countSupplier);
    }
  }

  static void updateSectorRootFlags(List<TreeNode> classification){
    TreeNode child = null;
    for (TreeNode n : classification) {
      if (child != null && child.getSectorKey() != null && !Objects.equals(child.getSectorKey(), n.getSectorKey())) {
        child.setSectorRoot(true);
      }
      child = n;
    }
    // child is now the root of the classification. If there still is a sectorKey its the root
    if (child != null && child.getSectorKey() != null) {
      child.setSectorRoot(true);
    }
  }

  static void updateSectorRootFlags(Integer parentSectorKey, List<TreeNode> children){
    for (TreeNode c : children) {
      if (c.getSectorKey() != null && !Objects.equals(c.getSectorKey(), parentSectorKey)) {
        c.setSectorRoot(true);
      }
    }
  }

  private static TreeNode placeholder(TreeNode parent, @Nullable Rank parentPlaceholderRank, Rank rank){
    return placeholder(parent.getDatasetKey(), parent.getSectorKey(), parent.getId(), parentPlaceholderRank, rank, 1);
  }

  private static TreeNode placeholder(TreeNode parent, TreeNode sibling, int childCount, List<Rank> placeholderParentRanks){
    Collections.sort(placeholderParentRanks);
    Rank placeholderParentRank = placeholderParentRanks.size() > 1 ? placeholderParentRanks.get(placeholderParentRanks.size() - 2) : null;
    return placeholder(sibling.getDatasetKey(), parent.getSectorKey(), sibling.getParentId(), placeholderParentRank, sibling.getRank(), childCount);
  }

  /**
   * Builds a virtual placeholder node mostly based on the rank and the first real usage id of its parents
   *
   * @param datasetKey
   * @param sectorKey
   * @param parentID the real usage id of the parent
   * @param parentPlaceholderRank the next higher placeholder rank if there are any other ranks in between this placeholder and the real parent
   * @param rank rank of the placeholder to be generated
   * @param childCount number of direct children for this placeholder
   */
  private static TreeNode placeholder(Integer datasetKey, Integer sectorKey, String parentID, @Nullable Rank parentPlaceholderRank, Rank rank, int childCount){
    TreeNode tn = new TreeNode.PlaceholderNode();
    tn.setDatasetKey(datasetKey);
    tn.setSectorKey(sectorKey);
    tn.setId(RankID.buildID(parentID, rank));
    tn.setParentId(parentPlaceholderRank == null ? parentID : RankID.buildID(parentID, parentPlaceholderRank));
    tn.setRank(rank);
    tn.setChildCount(childCount);
    tn.setStatus(TaxonomicStatus.ACCEPTED);
    return tn;
  }
}
