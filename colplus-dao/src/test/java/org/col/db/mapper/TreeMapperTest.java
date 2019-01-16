package org.col.db.mapper;

import java.util.List;

import org.col.api.TestEntityGenerator;
import org.col.api.model.*;
import org.junit.Before;
import org.junit.Test;

import static org.col.api.vocab.Datasets.DRAFT_COL;
import static org.junit.Assert.*;

public class TreeMapperTest extends MapperTestBase<TreeMapper> {
  
  private ColSource source;
  private final int dataset11 = TestEntityGenerator.DATASET11.getKey();
  
  public TreeMapperTest() {
    super(TreeMapper.class);
  }
  
  @Before
  public void initSource() {
    source = ColSourceMapperTest.create(dataset11);
    mapper(ColSourceMapper.class).create(source);
    
    commit();
  }
  
  @Test
  public void root() {
    assertEquals(2, valid(mapper().root(dataset11, new Page())).size());
    TreeNode tn = mapper().root(dataset11, new Page()).get(0);
    assertEquals(dataset11, (int) tn.getDatasetKey());
    assertNotNull(tn.getId());
    assertNull(tn.getParentId());
  }
  
  @Test
  public void parents() {
    assertEquals(1, valid(mapper().parents(dataset11, "root-1")).size());
  }
  
  @Test
  public void children() {
    assertEquals(0, valid(mapper().children(dataset11, "root-1", new Page())).size());
  }
  
  @Test
  public void draftWithSector() {
    populateDraftTree();
    
    SectorMapper sm = mapper(SectorMapper.class);
    
    Sector s1 = TestEntityGenerator.setUserDate(new Sector());
    s1.setColSourceKey(source.getKey());
    s1.setSubject(nameref("root-1"));
    s1.setTarget(nameref("t4"));
    sm.create(s1);
    
    Sector s2 = TestEntityGenerator.setUserDate(new Sector());
    s2.setColSourceKey(source.getKey());
    s2.setSubject(nameref("root-2"));
    s2.setTarget(nameref("t5"));
    sm.create(s2);
    commit();
    
    List<TreeNode.TreeNodeMybatis> nodes = mapper().children(DRAFT_COL, "t1", new Page());
    assertEquals(1, nodes.size());
    noSector(nodes);
  
    nodes = mapper().children(DRAFT_COL, "t2", new Page());
    assertEquals(1, nodes.size());
    noSector(nodes);
    
    nodes = mapper().children(DRAFT_COL, "t3", new Page());
    assertEquals(2, nodes.size());
    sectors(nodes);
    
    nodes = mapper().parents(DRAFT_COL, "t4");
    assertEquals(4, nodes.size());
    valid(nodes);
    assertNotNull(nodes.get(0).getSector());
    assertNull(nodes.get(1).getSector());
  }
  
  private List<TreeNode.TreeNodeMybatis> sectors(List<TreeNode.TreeNodeMybatis> nodes) {
    valid(nodes);
    for (TreeNode n : nodes) {
      assertNotNull(n.getSector());
    }
    return nodes;
  }

  private List<TreeNode.TreeNodeMybatis> noSector(List<TreeNode.TreeNodeMybatis> nodes) {
    valid(nodes);
    for (TreeNode n : nodes) {
      assertNull(n.getSector());
    }
    return nodes;
  }
  
  private List<TreeNode.TreeNodeMybatis> valid(List<TreeNode.TreeNodeMybatis> nodes) {
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