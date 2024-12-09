package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.collection.CollectionUtils;
import life.catalogue.junit.MybatisTestUtils;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.UUID;

import org.junit.Test;

import static life.catalogue.api.vocab.Datasets.COL;
import static org.junit.Assert.*;

public class TreeMapperTest extends MapperTestBase<TreeMapper> {
  
  private final int dataset11 = TestEntityGenerator.DATASET11.getKey();
  private final DSID<String> sp1 = TestEntityGenerator.TAXON1;
  private final DSID<String> sp2 = TestEntityGenerator.TAXON2;
  private final DSID<String> d11 = DSID.of(dataset11, null);

  public TreeMapperTest() {
    super(TreeMapper.class);
  }
  
  @Test
  public void get() {
    TreeNode tn = mapper().get(Datasets.COL, TreeNode.Type.SOURCE, DSID.of(dataset11, "root-1"), true);
    assertEquals(dataset11, (int) tn.getDatasetKey());
    assertNotNull(tn.getId());
    assertNull(tn.getParentId());
    // make sure we get the html markup
    assertEquals("Malus sylvestris", tn.getName());
    assertEquals("<i>Malus sylvestris</i>", tn.getLabelHtml());
    assertNull(tn.getAuthorship());
    assertTrue(tn.getSourceDatasetKeys().isEmpty());

    // this does not create taxon metrics - all null below, but the project calls which use dynamic counts!
    MybatisTestUtils.populateDraftTree(session());

    tn = mapper().get(Datasets.COL, TreeNode.Type.PROJECT, DSID.colID("t4"), true);
    assertTrue(tn.getSourceDatasetKeys().isEmpty());
    assertEquals(0, tn.getChildCount());
    assertNull(tn.getCount());

    tn = mapper().get(Datasets.COL, TreeNode.Type.PROJECT, DSID.colID("t3"), true);
    assertTrue(tn.getSourceDatasetKeys().isEmpty());
    assertEquals(2, tn.getChildCount());
    assertNull(tn.getCount());

    tn = mapper().get(Datasets.COL, TreeNode.Type.PROJECT, DSID.colID("t2"), true);
    assertTrue(tn.getSourceDatasetKeys().isEmpty());
    assertEquals(1, tn.getChildCount());
    assertNull(tn.getCount());

    tn = mapper().get(Datasets.COL, null, DSID.colID("t1"), true);
    assertTrue(tn.getSourceDatasetKeys().isEmpty());
    assertNull(tn.getSectorKey());
    assertNull(tn.getSectorMode());
    assertNull(tn.getDecision());
    assertEquals("t1", tn.getId());
  }
  
  @Test
  public void root() {
    assertEquals(2, valid(mapper().children(Datasets.COL, TreeNode.Type.SOURCE, d11, true, true, new Page())).size());
    TreeNode tn = mapper().children(Datasets.COL, TreeNode.Type.SOURCE, d11,  true, true, new Page()).get(0);
    assertEquals(dataset11, (int) tn.getDatasetKey());
    assertNotNull(tn.getId());
    assertNull(tn.getParentId());
    // make sure we get the html markup
    assertEquals("Larus fuscus", tn.getName());
    assertEquals("†<i>Larus fuscus</i>", tn.getLabelHtml());
    assertNull(tn.getAuthorship());

    assertEquals(1, valid(mapper().children(Datasets.COL, TreeNode.Type.SOURCE, d11,  false, true, new Page())).size());
    tn = mapper().children(Datasets.COL, TreeNode.Type.SOURCE, d11,  false, true, new Page()).get(0);
    assertEquals(dataset11, (int) tn.getDatasetKey());
    assertNotNull(tn.getId());
    assertNull(tn.getParentId());
    // make sure we get the html markup
    assertEquals("Malus sylvestris", tn.getName());
    assertEquals("<i>Malus sylvestris</i>", tn.getLabelHtml());
    assertNull(tn.getAuthorship());
  }
  
  @Test
  public void parents() {
    assertEquals(1, valid(mapper().classification(Datasets.COL, TreeNode.Type.SOURCE, DSID.of(dataset11, "root-1"), true)).size());
  }
  
  @Test
  public void children() {
    assertEquals(0, valid(mapper().children(Datasets.COL, TreeNode.Type.SOURCE, DSID.of(dataset11, "root-1"), true, true, new Page())).size());
    assertEquals(0, valid(mapper().childrenWithPlaceholder(Datasets.COL, TreeNode.Type.SOURCE, DSID.of(dataset11, "root-1"), null, true, true, new Page())).size());

    assertEquals(0, valid(mapper().children(Datasets.COL, TreeNode.Type.PROJECT, DSID.of(Datasets.COL, "root-1"), true, true, new Page())).size());
  }

  @Test
  public void childrenSectorsAndRank() {
    // add some test usages
    addUsage(sp1, Rank.SUBSPECIES, null);
    addUsage(sp1, Rank.SUBSPECIES, null);
    addUsage(sp1, Rank.SUBSPECIES, null);

    addUsage(sp2, Rank.SUBSPECIES, null);
    addUsage(sp2, Rank.SUBSPECIES, null);

    var s1 = SectorMapperTest.create(sp2, DSID.of(dataset11, "12347654"));
    mapper(SectorMapper.class).create(s1);

    addUsage(sp2, Rank.VARIETY, s1.getId());
    addUsage(sp2, Rank.VARIETY, s1.getId());

    var s2 = SectorMapperTest.create(sp2, DSID.of(dataset11, "1234"));
    mapper(SectorMapper.class).create(s2);
    addUsage(sp2, Rank.FORM, s2.getId());

    assertEquals(List.of(Rank.SUBSPECIES), mapper().childrenRanks(sp1, null, true));
    assertEquals(List.of(Rank.SUBSPECIES), mapper().childrenRanks(sp1, Rank.SUBSPECIES, true));
    assertEquals(List.of(Rank.SUBSPECIES), mapper().childrenRanks(sp1, Rank.VARIETY, true));
    assertEquals(List.of(), mapper().childrenRanks(sp1, Rank.SPECIES, true));

    assertEquals(List.of(Rank.SUBSPECIES, Rank.VARIETY, Rank.FORM), mapper().childrenRanks(sp2, null, true));
    assertEquals(List.of(Rank.SUBSPECIES, Rank.VARIETY), mapper().childrenRanks(sp2, Rank.VARIETY, true));
    assertEquals(List.of(Rank.SUBSPECIES, Rank.VARIETY, Rank.FORM), mapper().childrenRanks(sp2, Rank.CHEMOFORM, true));
    assertEquals(List.of(Rank.SUBSPECIES), mapper().childrenRanks(sp2, Rank.SUBSPECIES, true));
    assertEquals(List.of(), mapper().childrenRanks(sp2, Rank.GENUS, true));

    assertEquals(CollectionUtils.list((Integer)null), mapper().childrenSectors(sp1, null));
    assertEquals(CollectionUtils.list(s1.getId(), s2.getId(), null), mapper().childrenSectors(sp2, null));
    assertEquals(CollectionUtils.list(s1.getId(), s2.getId(), null), mapper().childrenSectors(sp2, Rank.ORDER));
  }

  private void addUsage(DSID<String> parent, Rank rank, Integer sectorKey) {
    Name n = new Name();
    n.setId(UUID.randomUUID().toString());
    n.setDatasetKey(parent.getDatasetKey());
    n.setRank(rank);
    n.setScientificName("Test name");
    n.setType(NameType.SCIENTIFIC);
    n.setOrigin(Origin.OTHER);
    n.applyUser(Users.TESTER);
    mapper(NameMapper.class).create(n);

    Taxon t = new Taxon();
    t.setName(n);
    t.setId(n.getId());
    t.setDatasetKey(n.getDatasetKey());
    t.setOrigin(n.getOrigin());
    t.setStatus(TaxonomicStatus.ACCEPTED);
    t.applyUser(Users.TESTER);
    t.setParentId(parent.getId());
    t.setSectorKey(sectorKey);
    mapper(TaxonMapper.class).create(t);
  }

  @Test
  public void draftWithSector() {
    MybatisTestUtils.populateDraftTree(session());
    
    SectorMapper sm = mapper(SectorMapper.class);
    
    Sector s1 = TestEntityGenerator.setUserDate(new Sector());
    s1.setDatasetKey(COL);
    s1.setSubjectDatasetKey(dataset11);
    s1.setSubject(nameref("root-1"));
    s1.setTarget(nameref("t4"));
    sm.create(s1);
    
    Sector s2 = TestEntityGenerator.setUserDate(new Sector());
    s2.setDatasetKey(COL);
    s2.setSubjectDatasetKey(dataset11);
    s2.setSubject(nameref("root-2"));
    s2.setTarget(nameref("t5"));
    sm.create(s2);
    commit();
    
    List<TreeNode> nodes = mapper().children(Datasets.COL, TreeNode.Type.PROJECT, DSID.colID("t1"), true, true, new Page());
    assertEquals(1, nodes.size());
    noSectors(nodes);
  
    nodes = mapper().children(Datasets.COL, TreeNode.Type.PROJECT, DSID.colID("t2"), true, false, new Page());
    assertEquals(1, nodes.size());
    noSectors(nodes);
    
    nodes = mapper().children(Datasets.COL, TreeNode.Type.PROJECT, DSID.colID("t3"), true, true, new Page());
    assertEquals(2, nodes.size());
    valid(nodes);

    nodes = mapper().classification(Datasets.COL, TreeNode.Type.PROJECT, DSID.colID("t4"), false);
    assertEquals(4, nodes.size());
    valid(nodes);
  }
  
  @Test
  public void sourceWithDecisions() {
  
    MybatisTestUtils.populateDraftTree(session());
    MybatisTestUtils.populateTestTree(dataset11, session());
    
    SectorMapper sm = mapper(SectorMapper.class);
    DecisionMapper dm = mapper(DecisionMapper.class);
    
    Sector s = TestEntityGenerator.setUserDate(new Sector());
    s.setDatasetKey(COL);
    s.setSubjectDatasetKey(dataset11);
    s.setSubject(nameref("t2"));
    s.setTarget(nameref("root-1"));
    sm.create(s);
  
    EditorialDecision d1 = TestEntityGenerator.setUser(new EditorialDecision());
    d1.setDatasetKey(COL);
    d1.setSubjectDatasetKey(dataset11);
    d1.setSubject(nameref("t2"));
    d1.setMode(EditorialDecision.Mode.UPDATE);
    dm.create(d1);
  
    EditorialDecision d2 = TestEntityGenerator.setUser(new EditorialDecision());
    d2.setDatasetKey(COL);
    d2.setSubjectDatasetKey(dataset11);
    d2.setSubject(nameref("t3"));
    d2.setMode(EditorialDecision.Mode.BLOCK);
    dm.create(d2);

    
    List<TreeNode> nodes = mapper().children(Datasets.COL, TreeNode.Type.SOURCE, DSID.of(dataset11, "t1"), true, true, new Page());
    assertEquals(1, nodes.size());
    assertEquals(s.getId(), nodes.get(0).getSectorKey());
    assertEquals(s.getMode(), nodes.get(0).getSectorMode());

    DecisionMapperTest.removeCreatedProps(d1);
    DecisionMapperTest.removeCreatedProps(nodes.get(0).getDecision());
    printDiff(d1, nodes.get(0).getDecision());
    equals(d1, nodes.get(0).getDecision());
    
    nodes = mapper().classification(Datasets.COL, TreeNode.Type.SOURCE, DSID.of(dataset11, "t4"), true);
    assertEquals(4, nodes.size());
  
    assertNull(nodes.get(0).getSectorKey());
    assertNull(nodes.get(0).getSectorMode());
    assertNull(nodes.get(1).getSectorKey());
    assertNull(nodes.get(1).getSectorMode());
    assertEquals(s.getId(), nodes.get(2).getSectorKey());
    assertEquals(s.getMode(), nodes.get(2).getSectorMode());
    assertNull(nodes.get(3).getSectorKey());
    assertNull(nodes.get(3).getSectorMode());

    assertNull(nodes.get(0).getDecision());
    equals(d2, nodes.get(1).getDecision());
    equals(d1, nodes.get(2).getDecision());
  
    nodes = mapper().children(Datasets.COL, TreeNode.Type.SOURCE, DSID.of(dataset11, "t2"), true, true, new Page());
    noSectors(noSectors(nodes));
  }

  private static void equals(EditorialDecision d1, EditorialDecision d2){
    assertEquals(DecisionMapperTest.removeCreatedProps(d1), DecisionMapperTest.removeCreatedProps(d2));
  }
  
  SpeciesEstimate newEstimate(String id){
    SpeciesEstimate s = new SpeciesEstimate();
    s.setDatasetKey(COL);
    s.setEstimate(5678);
    s.setTarget(SimpleNameLink.of(id, "Abies alba", Rank.SPECIES));
    s.applyUser(TestEntityGenerator.USER_USER);
    return s;
  }
  
  @Test
  public void withEstimates() {
    
    MybatisTestUtils.populateDraftTree(session());
  
    EstimateMapper em = mapper(EstimateMapper.class);
    
    SpeciesEstimate s1 = newEstimate("t1");
    em.create(s1);
  
    SpeciesEstimate s2 = newEstimate("t1");
    em.create(s2);
  
    SpeciesEstimate s3 = newEstimate("t2");
    em.create(s3);

    List<TreeNode> nodes = mapper().children(Datasets.COL, TreeNode.Type.PROJECT, DSID.colID(null), true, true, new Page());
    assertEquals(1, nodes.size());
    assertEquals(2, nodes.get(0).getEstimates().size());
    for (SpeciesEstimate s : nodes.get(0).getEstimates()) {
      assertEquals(s1.getEstimate(), s.getEstimate());
    }
  }

  /**
   * Tests for equality but removes user dates which are usually set by the db
   */
  private static <T extends UserManaged> void equals(T o1, T o2) {
    assertEquals(TestEntityGenerator.nullifyDate(o1), TestEntityGenerator.nullifyDate(o2));
  }
  
  private static List<TreeNode> noSectors(List<TreeNode> nodes) {
    valid(nodes);
    for (TreeNode n : nodes) {
      assertNull(n.getSectorKey());
      assertNull(n.getSectorDatasetKey());
    }
    return nodes;
  }

  private static List<TreeNode> sectors(List<TreeNode> nodes) {
    valid(nodes);
    for (TreeNode n : nodes) {
      assertNotNull(n.getSectorKey());
      assertNotNull(n.getSectorMode());
      assertNotNull(n.getSectorDatasetKey());
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
  
  static SimpleNameLink nameref(String id) {
    SimpleNameLink nr = new SimpleNameLink();
    nr.setId(id);
    return nr;
  }
  
}