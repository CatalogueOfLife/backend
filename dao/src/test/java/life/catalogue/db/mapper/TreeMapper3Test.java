package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.TreeNode;
import life.catalogue.common.collection.CollectionUtils;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.tree.TxtTreeDataRule;

import org.apache.commons.collections4.ListUtils;

import org.gbif.nameparser.api.Rank;

import org.junit.ClassRule;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class TreeMapper3Test extends MapperTestBase<TreeMapper> {
  static final int dkey = 10;

  @ClassRule
  public static TxtTreeDataRule treeRule = TxtTreeDataRule.create(Map.of(
    dkey, TxtTreeDataRule.TreeData.AVES
  ), true);

  public TreeMapper3Test() {
    super(TreeMapper.class, TestDataRule.keep());
  }

  @Test
  public void children() {
    TreeMapper tm = mapper(TreeMapper.class);
    Page page = new Page(0, 100);
    for (TreeNode.Type type : TreeNode.types()) {
      DSID<String> key = DSID.of(dkey, null);
      List<TreeNode> root = noSectors(tm.children(dkey, type, key, true, page));
      assertEquals(1, root.size());
      // IDs are the line numbers of tree files !!!
      assertEquals("1", root.get(0).getId());
      assertEquals("Aves", root.get(0).getName());

      var children = noSectors(tm.children(dkey, type, key.id("1"), true, page));
      assertChildOrder(children, List.of("2", "8"));

      children = noSectors(tm.children(dkey, type, key.id("2"), true, page));
      assertChildOrder(children, List.of("3", "4", "5", "6", "7"));

      children = noSectors(tm.children(dkey, type, key.id("8"), true, page));
      assertChildOrder(children, List.of("9", "12"));

      children = noSectors(tm.children(dkey, type, key.id("12"), true, page));
      assertChildOrder(children, 13, 48);
    }
  }

  void assertChildOrder(List<TreeNode> children, int expectedStartID, int expectedEndID){
    assertChildOrder(children, CollectionUtils.range(expectedStartID, expectedEndID, 1).stream().map(Object::toString).collect(Collectors.toList()));
  }

  void assertChildOrder(List<TreeNode> children, List<String> expected){
    assertEquals(expected.size(), children.size());
    int idx = 0;
    for (var c : children) {
      var id = expected.get(idx++);
      assertEquals(id, c.getId());
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
  
}