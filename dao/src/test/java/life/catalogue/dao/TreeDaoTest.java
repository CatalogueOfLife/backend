package life.catalogue.dao;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.TxtTreeDataRule;
import life.catalogue.db.mapper.SectorMapperTest;
import org.gbif.nameparser.api.Rank;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class TreeDaoTest {
  static final int catKey = 10;
  static final int TRILOBITA = 100;
  static final int MAMMALIA = 101;

  static final Page PAGE = new Page(0, 100);

  // sets up pg server first, then loads the trees.
  @ClassRule
  public static RuleChain chain= RuleChain
      .outerRule(new PgSetupRule())
      .around(new TxtTreeDataRule(Map.of(
        catKey, TxtTreeDataRule.TreeData.ANIMALIA,
        TRILOBITA, TxtTreeDataRule.TreeData.TRILOBITA,
        MAMMALIA, TxtTreeDataRule.TreeData.MAMMALIA
        ))
      );

  @BeforeClass
  public static void initSector() {
    System.out.println("Setup sectors & decisions");
    SectorDao sdao = new SectorDao(PgSetupRule.getSqlSessionFactory());
    // sector subject "Trilobita unassigned family"
    createSector(sdao, DSID.key(TRILOBITA, RankID.buildID("1", Rank.FAMILY)), DSID.key(catKey,"9"));
    // sector subject "Agnostoidea superfamily"
    createSector(sdao, DSID.key(TRILOBITA,"3"), DSID.key(catKey,"9"));
  }

  TreeDao dao = new TreeDao(PgSetupRule.getSqlSessionFactory());


  @Test
  public void root() {
    for (TreeNode.Type type : TreeNode.types()) {
      ResultPage<TreeNode> root = noSectors(dao.root(catKey, catKey, type, PAGE));
      assertEquals(1, root.size());
      assertEquals("1", root.getResult().get(0).getId());
      assertEquals("Animalia", root.getResult().get(0).getName());

      root = noSectors(dao.root(TRILOBITA, catKey, type, PAGE));
      assertEquals(1, root.size());
      assertEquals("1", root.getResult().get(0).getId());
      assertEquals("Trilobita", root.getResult().get(0).getName());
    }
  }

  @Test
  public void parents() {
    for (boolean placeholder : List.of(false, true)) {
      for (TreeNode.Type type : TreeNode.types()) {
        List<TreeNode> parents = valid(dao.classification(DSID.key(TRILOBITA, "10"), catKey, placeholder, type));
        assertEquals(6, parents.size());
        assertEquals(Rank.SPECIES, parents.get(0).getRank());
        assertEquals(Rank.GENUS, parents.get(1).getRank());
      }
    }

    // expect placeholders
    for (TreeNode.Type type : TreeNode.types()) {
      List<TreeNode> parents = valid(dao.classification(DSID.key(TRILOBITA, "61"), catKey, true, type));
      assertEquals(5, parents.size());
      assertNode(assertNoSector(parents.get(0)), Rank.SPECIES, "Amechilus palaora");
      assertEquals(Rank.GENUS, parents.get(1).getRank());
      assertPlaceholder(parents.get(2), Rank.FAMILY);
      if (type == TreeNode.Type.SOURCE) {
        assertSector(parents.get(2));
      } else {
        assertNoSector(parents.get(2));
      }
      assertPlaceholder(assertNoSector(parents.get(3)), Rank.ORDER);
      assertNode(assertNoSector(parents.get(4)), Rank.CLASS, "Trilobita");
    }

    // test calling a placeholder id
    List<TreeNode> parents = valid(dao.classification(DSID.key(TRILOBITA, RankID.buildID("2", Rank.FAMILY)), catKey, true, null));
    assertEquals(4, parents.size());
    assertPlaceholder(assertNoSector(parents.get(0)), Rank.FAMILY);
    assertPlaceholder(assertNoSector(parents.get(1)), Rank.SUPERFAMILY);
    assertEquals(Rank.ORDER, assertNoSector(parents.get(2)).getRank());
    assertNode(assertNoSector(parents.get(3)), Rank.CLASS, "Trilobita");
  }

  @Test
  public void children() {
    ResultPage<TreeNode> children = valid(dao.children(DSID.key(TRILOBITA, RankID.buildID("1", Rank.ORDER)), catKey, true, null, PAGE));
    assertEquals(2, children.size());
    assertNode(children.getResult().get(0), Rank.FAMILY, "Scutelluidae");
    assertPlaceholder(children.getResult().get(1), Rank.FAMILY);

    children = valid(dao.children(children.getResult().get(1), catKey, true, null, PAGE));
    assertEquals(2, children.size());
    assertNode(children.getResult().get(0), Rank.GENUS, "Amechilus");
    assertNode(children.getResult().get(1), Rank.GENUS, "Deltacare");
  }

  private static void assertPlaceholder(TreeNode n, Rank rank) {
    assertTrue(n instanceof TreeNode.PlaceholderNode);
    assertEquals(rank, n.getRank());
  }

  private static void assertNode(TreeNode n, Rank rank, String name) {
    assertFalse(n instanceof TreeNode.PlaceholderNode);
    assertEquals(rank, n.getRank());
    assertEquals(name, n.getName());
  }

  private static TreeNode assertSector(TreeNode node) {
    assertNotNull(node.getSectorKey());
    return node;
  }

  private static TreeNode assertNoSector(TreeNode node) {
    assertNull(node.getSectorKey());
    return node;
  }

  private static ResultPage<TreeNode> noSectors(ResultPage<TreeNode> nodes) {
    noSectors(nodes.getResult());
    return nodes;
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

  private static ResultPage<TreeNode> valid(ResultPage<TreeNode> nodes) {
    valid(nodes.getResult());
    return nodes;
  }

  private static Sector createSector(SectorDao dao, DSID<String> subject, DSID<String> target){
    Sector s = SectorMapperTest.create();

    s.setSubjectDatasetKey(subject.getDatasetKey());
    s.getSubject().setId(subject.getId());

    s.setDatasetKey(target.getDatasetKey());
    s.getTarget().setId(target.getId());

    dao.create(s, Users.TESTER);
    return s;
  }
}