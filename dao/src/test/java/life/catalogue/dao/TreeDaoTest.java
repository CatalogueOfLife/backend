package life.catalogue.dao;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.tree.SectorDataRule;
import life.catalogue.db.tree.TxtTreeDataRule;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.SectorMapperTest;
import org.apache.ibatis.session.SqlSession;
import org.gbif.nameparser.api.Rank;
import org.junit.BeforeClass;
import org.junit.ClassRule;
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

  private static final SectorDataRule sectorRule = new SectorDataRule(List.of(
    // #0 sector subject "Trilobita unassigned family"
    SectorDataRule.create(Sector.Mode.UNION, DSID.key(TRILOBITA, RankID.buildID("1", Rank.FAMILY)), DSID.key(catKey,"9")),
    // #1 sector subject "Agnostoidea superfamily"
    SectorDataRule.create(Sector.Mode.ATTACH, DSID.key(TRILOBITA,"3"), DSID.key(catKey,"9")),
    // #2 sector subject "Agnostida placeholder superfamily"
    SectorDataRule.create(Sector.Mode.UNION, DSID.key(TRILOBITA,RankID.buildID("2", Rank.SUPERFAMILY)), DSID.key(catKey,"9")),

    // #3 sector subject Mammalia union into Mammalia of catalogue
    SectorDataRule.create(Sector.Mode.UNION, DSID.key(MAMMALIA,"2"), DSID.key(catKey,"18"))
  ));

  // sets up pg server first, then loads the trees.
  @ClassRule
  public static RuleChain chain= RuleChain
      .outerRule(new PgSetupRule())
      .around(new TxtTreeDataRule(Map.of(
        catKey, TxtTreeDataRule.TreeData.ANIMALIA,
        TRILOBITA, TxtTreeDataRule.TreeData.TRILOBITA,
        MAMMALIA, TxtTreeDataRule.TreeData.MAMMALIA
        )))
      .around(sectorRule);

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
  public void classification() {
    for (boolean placeholder : List.of(false, true)) {
      for (TreeNode.Type type : TreeNode.types()) {
        List<TreeNode> classification = valid(dao.classification(DSID.key(TRILOBITA, "10"), catKey, placeholder, type));
        assertEquals(6, classification.size());
        assertEquals(Rank.SPECIES, classification.get(0).getRank());
        assertEquals(Rank.GENUS, classification.get(1).getRank());
      }
    }

    // expect placeholders
    for (TreeNode.Type type : TreeNode.types()) {
      List<TreeNode> classification = valid(dao.classification(DSID.key(TRILOBITA, "61"), catKey, true, type));
      assertEquals(5, classification.size());
      assertNode(assertNoSector(classification.get(0)), Rank.SPECIES, "Amechilus palaora");
      assertEquals(Rank.GENUS, classification.get(1).getRank());
      assertPlaceholder(classification.get(2), Rank.FAMILY);
      if (type == TreeNode.Type.SOURCE) {
        assertSector(classification.get(2));
      } else {
        assertNoSector(classification.get(2));
      }
      assertPlaceholder(assertNoSector(classification.get(3)), Rank.ORDER);
      assertNode(assertNoSector(classification.get(4)), Rank.CLASS, "Trilobita");
    }

    // Agnostida family placeholder
    for (TreeNode.Type type : TreeNode.types()) {
      List<TreeNode> classification = valid(dao.classification(DSID.key(TRILOBITA, RankID.buildID("2", Rank.FAMILY)), catKey, true, type));
      assertEquals(4, classification.size());
      assertPlaceholder(assertNoSector(classification.get(0)), Rank.FAMILY);
      assertPlaceholder(classification.get(1), Rank.SUPERFAMILY);
      if (type== TreeNode.Type.SOURCE) {
        assertSector(classification.get(1), sectorRule.sectorKey(2));
      } else {
        assertNoSector(classification.get(1));
      }
      assertEquals(Rank.ORDER, assertNoSector(classification.get(2)).getRank());
      assertNode(assertNoSector(classification.get(3)), Rank.CLASS, "Trilobita");
    }


    // browse the PROJECT CATALOGUE

    // mammalia with 2 placeholders below that should not show up
    for (TreeNode.Type type : TreeNode.types()) {
      List<TreeNode> classification = valid(dao.classification(DSID.key(catKey, "18"), catKey, true, type));
      assertEquals(3, classification.size());
      assertNode(assertNoSector(classification.get(0)), Rank.CLASS, "Mammalia");
      assertNode(assertNoSector(classification.get(1)), Rank.PHYLUM, "Chordata");
      assertNode(assertNoSector(classification.get(2)), Rank.KINGDOM, "Animalia");
    }

    // start with genus Amechilus in placeholder sector
    List<TreeNode> classification = valid(dao.classification(DSID.key(catKey, "100:60"), catKey, true, TreeNode.Type.CATALOGUE));
    assertEquals(6, classification.size());
    assertNode(classification.get(0), Rank.GENUS, "Amechilus");
    assertSector(classification.get(0), sectorRule.sectorKey(0));

    assertPlaceholder(assertNoSector(classification.get(1)), Rank.FAMILY);
    //TODO: assertSector(classification.get(1), sectorRule.sectorKey(0));

    assertPlaceholder(assertNoSector(classification.get(2)), Rank.SUPERFAMILY);

    assertNode(assertNoSector(classification.get(3)), Rank.CLASS, "Trilobita");

    assertNode(assertNoSector(classification.get(4)), Rank.PHYLUM, "Arthropoda");

    assertNode(assertNoSector(classification.get(5)), Rank.KINGDOM, "Animalia");
  }

  @Test
  public void children() {
    for (TreeNode.Type type : TreeNode.types()) {
      ResultPage<TreeNode> children = valid(dao.children(DSID.key(TRILOBITA, RankID.buildID("1", Rank.ORDER)), catKey, true, type, PAGE));
      assertEquals(2, children.size());
      assertNode(children.getResult().get(0), Rank.FAMILY, "Scutelluidae");
      assertPlaceholder(children.getResult().get(1), Rank.FAMILY);
      if (type == TreeNode.Type.SOURCE) {
        assertSector(children.getResult().get(1));
      } else {
        assertNoSector(children.getResult().get(1));
      }

      children = valid(dao.children(children.getResult().get(1), catKey, true, type, PAGE));
      assertEquals(2, children.size());
      assertNode(children.getResult().get(0), Rank.GENUS, "Amechilus");
      assertNode(children.getResult().get(1), Rank.GENUS, "Deltacare");
    }

    // browse the project catalogue
    ResultPage<TreeNode> children = valid(dao.children(DSID.key(catKey, "9"), catKey, true, TreeNode.Type.CATALOGUE, PAGE));
    assertEquals(2, children.size());
    assertNode(children.getResult().get(0), Rank.SUPERFAMILY, "Agnostoidea");
    assertSector(children.getResult().get(0), sectorRule.sectorKey(1));

    assertPlaceholder(children.getResult().get(1), Rank.SUPERFAMILY);
    assertNoSector(children.getResult().get(1));

    // mammalia with 2 placeholders below that should show up with sectorKeys
    children = valid(dao.children(DSID.key(catKey, "18"), catKey, true, TreeNode.Type.CATALOGUE, PAGE));
    assertEquals(3, children.size());

    assertNode(children.getResult().get(0), Rank.ORDER, "Carnivora");
    assertNoSector(children.getResult().get(0));

    assertNode(children.getResult().get(1), Rank.ORDER, "Carnivora");
    assertSector(children.getResult().get(1), sectorRule.sectorKey(3));

    assertPlaceholder(children.getResult().get(2), Rank.ORDER);
    assertSector(children.getResult().get(2), sectorRule.sectorKey(3));
  }

  private static void assertPlaceholder(TreeNode n, Rank rank) {
    assertTrue(n.isPlaceholder());
    assertEquals(rank, n.getRank());
  }

  private static void assertNode(TreeNode n, Rank rank, String name) {
    assertFalse(n.isPlaceholder());
    assertEquals(rank, n.getRank());
    assertEquals(name, n.getName());
  }

  private static TreeNode assertSector(TreeNode node, int sectorKey) {
    assertEquals( (Integer) sectorKey, node.getSectorKey());
    return node;
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

}