package org.col.dao;

import org.col.api.TestEntityGenerator;
import org.col.api.model.Sector;
import org.col.api.model.TreeNode;
import org.col.db.MybatisTestUtils;
import org.col.db.mapper.SectorMapperTest;
import org.col.db.mapper.TreeMapper;
import org.junit.Ignore;
import org.junit.Test;

import static org.col.api.vocab.Datasets.DRAFT_COL;
import static org.junit.Assert.assertEquals;

public class SectorDaoTest extends DaoTestBase {
  static int user = TestEntityGenerator.USER_EDITOR.getKey();
  
  @Test
  @Ignore
  public void popTree() {
    MybatisTestUtils.populateDraftTree(session());
  }
  
  @Test
  public void create() {
    MybatisTestUtils.populateDraftTree(session());
    
    SectorDao dao = new SectorDao(factory());
    Sector s = SectorMapperTest.create();
    s.getSubject().setId("root-1");
    s.getTarget().setId("t4");
    s.setDatasetKey(11);
    dao.create(s, user);
  
    s = SectorMapperTest.create();
    s.getSubject().setId("t2");
    s.getTarget().setId("t5");
    s.setDatasetKey(3);
    dao.create(s, user);
  
    TreeMapper tm = session().getMapper(TreeMapper.class);
  
    TreeNode tn = tm.get(DRAFT_COL, "t4");
    assertEquals(2, (int) tn.getDatasetSectors().get(11));
  
    tn = tm.get(DRAFT_COL, "t5");
    assertEquals(1, (int) tn.getDatasetSectors().get(11));
    assertEquals(1, (int) tn.getDatasetSectors().get(3));

    tn = tm.get(DRAFT_COL, "t3");
    assertEquals(3, (int) tn.getDatasetSectors().get(11));
    assertEquals(1, (int) tn.getDatasetSectors().get(3));
  
    tn = tm.get(DRAFT_COL, "t2");
    assertEquals(3, (int) tn.getDatasetSectors().get(11));
    assertEquals(1, (int) tn.getDatasetSectors().get(3));
  
    tn = tm.get(DRAFT_COL, "t1");
    assertEquals(3, (int) tn.getDatasetSectors().get(11));
    assertEquals(1, (int) tn.getDatasetSectors().get(3));
  }
}