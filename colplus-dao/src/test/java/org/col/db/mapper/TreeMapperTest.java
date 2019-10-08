package org.col.db.mapper;

import java.util.List;

import org.col.api.TestEntityGenerator;
import org.col.api.model.*;
import org.col.api.vocab.Datasets;
import org.col.db.MybatisTestUtils;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import static org.col.api.vocab.Datasets.DRAFT_COL;
import static org.junit.Assert.*;

public class TreeMapperTest extends MapperTestBase<TreeMapper> {
  
  private final int dataset11 = TestEntityGenerator.DATASET11.getKey();
  
  public TreeMapperTest() {
    super(TreeMapper.class);
  }
  
  @Test
  public void get() {
    TreeNode tn = mapper().get(Datasets.DRAFT_COL, DSID.key(dataset11, "root-1"));
    assertEquals(dataset11, (int) tn.getDatasetKey());
    assertNotNull(tn.getId());
    assertNull(tn.getParentId());
    // make sure we get the html markup
    assertEquals("<i>Malus</i> <i>sylvestris</i>", tn.getName());
    assertNull(tn.getDatasetSectors());
  
    MybatisTestUtils.populateDraftTree(session());

    tn = mapper().get(Datasets.DRAFT_COL, DSID.draftID("t4"));
    assertNotNull(tn.getDatasetSectors());
    assertEquals(1, (int) tn.getDatasetSectors().get(11));
  
    tn = mapper().get(Datasets.DRAFT_COL, DSID.draftID("t3"));
    assertEquals(2, (int) tn.getDatasetSectors().get(11));
  
    tn = mapper().get(Datasets.DRAFT_COL, DSID.draftID("t2"));
    assertEquals(2, (int) tn.getDatasetSectors().get(11));
  
    tn = mapper().get(Datasets.DRAFT_COL, DSID.draftID("t1"));
    assertEquals(2, (int) tn.getDatasetSectors().get(11));
  }
  
  @Test
  public void root() {
    assertEquals(2, valid(mapper().root(Datasets.DRAFT_COL, dataset11, new Page())).size());
    TreeNode tn = mapper().root(Datasets.DRAFT_COL, dataset11, new Page()).get(0);
    assertEquals(dataset11, (int) tn.getDatasetKey());
    assertNotNull(tn.getId());
    assertNull(tn.getParentId());
    // make sure we get the html markup
    assertEquals("<i>Larus</i> <i>fuscus</i>", tn.getName());
  }
  
  @Test
  public void parents() {
    assertEquals(1, valid(mapper().parents(Datasets.DRAFT_COL, DSID.key(dataset11, "root-1"))).size());
  }
  
  @Test
  public void children() {
    assertEquals(0, valid(mapper().children(Datasets.DRAFT_COL, DSID.key(dataset11, "root-1"), null, false, new Page())).size());
  }
  
  @Test
  public void draftWithSector() {
    MybatisTestUtils.populateDraftTree(session());
    
    SectorMapper sm = mapper(SectorMapper.class);
    
    Sector s1 = TestEntityGenerator.setUserDate(new Sector());
    s1.setDatasetKey(DRAFT_COL);
    s1.setSubjectDatasetKey(dataset11);
    s1.setSubject(nameref("root-1"));
    s1.setTarget(nameref("t4"));
    sm.create(s1);
    
    Sector s2 = TestEntityGenerator.setUserDate(new Sector());
    s2.setDatasetKey(DRAFT_COL);
    s2.setSubjectDatasetKey(dataset11);
    s2.setSubject(nameref("root-2"));
    s2.setTarget(nameref("t5"));
    sm.create(s2);
    commit();
    
    List<TreeNode> nodes = mapper().children(Datasets.DRAFT_COL, DSID.draftID("t1"), null,false, new Page());
    assertEquals(1, nodes.size());
    noSectorKeys(nodes);
  
    nodes = mapper().children(Datasets.DRAFT_COL, DSID.draftID("t2"), null,false, new Page());
    assertEquals(1, nodes.size());
    noSectorKeys(nodes);
    
    nodes = mapper().children(Datasets.DRAFT_COL, DSID.draftID("t3"), null,false, new Page());
    assertEquals(2, nodes.size());
    
    nodes = mapper().parents(Datasets.DRAFT_COL, DSID.draftID("t4"));
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
    s.setDatasetKey(DRAFT_COL);
    s.setSubjectDatasetKey(dataset11);
    s.setSubject(nameref("t2"));
    s.setTarget(nameref("root-1"));
    sm.create(s);
  
    EditorialDecision d1 = TestEntityGenerator.setUser(new EditorialDecision());
    d1.setDatasetKey(DRAFT_COL);
    d1.setSubjectDatasetKey(dataset11);
    d1.setSubject(nameref("t2"));
    d1.setMode(EditorialDecision.Mode.UPDATE);
    dm.create(d1);
  
    EditorialDecision d2 = TestEntityGenerator.setUser(new EditorialDecision());
    d2.setDatasetKey(DRAFT_COL);
    d2.setSubjectDatasetKey(dataset11);
    d2.setSubject(nameref("t3"));
    d2.setMode(EditorialDecision.Mode.BLOCK);
    dm.create(d2);

    
    List<TreeNode> nodes = mapper().children(Datasets.DRAFT_COL, DSID.key(dataset11, "t1"), null,false, new Page());
    assertEquals(1, nodes.size());
    assertEquals(s.getKey(), nodes.get(0).getSectorKey());
    equals(d1, nodes.get(0).getDecision());
    
    nodes = mapper().parents(Datasets.DRAFT_COL, DSID.key(dataset11, "t4"));
    assertEquals(4, nodes.size());
  
    assertNull(nodes.get(0).getSectorKey());
    assertNull(nodes.get(1).getSectorKey());
    assertEquals(s.getKey(), nodes.get(2).getSectorKey());
    assertNull(nodes.get(3).getSectorKey());
  
    assertNull(nodes.get(0).getDecision());
    equals(d2, nodes.get(1).getDecision());
    equals(d1, nodes.get(2).getDecision());
  
    nodes = mapper().children(Datasets.DRAFT_COL, DSID.key(dataset11, "t2"), null,false, new Page());
    noSectors(noSectorKeys(nodes));
  }
  
  SpeciesEstimate newEstimate(String id){
    SpeciesEstimate s = new SpeciesEstimate();
    s.setDatasetKey(DRAFT_COL);
    s.setEstimate(5678);
    s.setTarget(new SimpleName(id, "Abies alba", Rank.SPECIES));
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

    List<TreeNode> nodes = mapper().root(Datasets.DRAFT_COL, DRAFT_COL, new Page());
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
  
  private static List<TreeNode> noSectorKeys(List<TreeNode> nodes) {
    valid(nodes);
    for (TreeNode n : nodes) {
      assertNull(n.getSectorKey());
    }
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
  
  private static SimpleName nameref(String id) {
    SimpleName nr = new SimpleName();
    nr.setId(id);
    return nr;
  }
  
}