package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.TreeNode;
import life.catalogue.junit.TestDataRule;
import life.catalogue.junit.TxtTreeDataRule;

import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Map;

import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.*;

public class TreeMapper2Test extends MapperTestBase<TreeMapper> {
  static final int catKey = 10;
  static final int TRILOBITA = 100;
  static final int MAMMALIA = 101;

  @ClassRule
  public static TxtTreeDataRule treeRule = TxtTreeDataRule.create(Map.of(
    catKey, TxtTreeDataRule.TreeData.ANIMALIA,
    TRILOBITA, TxtTreeDataRule.TreeData.TRILOBITA,
    MAMMALIA, TxtTreeDataRule.TreeData.MAMMALIA
  ));

  public TreeMapper2Test() {
    super(TreeMapper.class, TestDataRule.keep());
  }

  @Test
  public void rootChildren() {
    TreeMapper tm = mapper(TreeMapper.class);
    Page page = new Page(0, 100);
    DSID<String> rootID = DSID.of(catKey, null);
    DSID<String> trilobitaRootID = DSID.of(TRILOBITA, null);
    for (TreeNode.Type type : TreeNode.types()) {
      List<TreeNode> root = noSectors(tm.children(catKey, type, rootID, true, page));
      assertEquals(1, root.size());
      assertEquals("1", root.get(0).getId());
      assertEquals("Animalia", root.get(0).getName());

      root = noSectors(tm.children(catKey, type, trilobitaRootID, true, page));
      assertEquals(1, root.size());
      assertEquals("1", root.get(0).getId());
      assertEquals("Trilobita", root.get(0).getName());
    }
  }

  @Test
  public void parents() {
    TreeMapper tm = mapper(TreeMapper.class);
    for (TreeNode.Type type : TreeNode.types()) {
      List<TreeNode> parents = valid(tm.classification(catKey, type, DSID.of(TRILOBITA, "10")));
      assertEquals(6, parents.size());
      assertEquals(Rank.SPECIES, parents.get(0).getRank());
      assertEquals(Rank.GENUS, parents.get(1).getRank());
    }
  }
  
  private static List<TreeNode> noSectors(List<TreeNode> nodes) {
    valid(nodes);
    for (TreeNode n : nodes) {
      assertNull(n.getSectorKey());
    }
    return nodes;
  }

  private static List<TreeNode> valid(List<TreeNode> nodes) {
    for (TreeNode n : nodes) {
      assertNotNull(n.getId());
      assertNotNull(n.getDatasetKey());
      assertNotNull(n.getName());
    }
    return nodes;
  }

  static SimpleName nameref(String id) {
    SimpleName nr = new SimpleName();
    nr.setId(id);
    return nr;
  }
  
}