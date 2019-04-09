package org.col.dao;

import org.col.api.model.Sector;
import org.col.api.model.TreeNode;
import org.col.db.MybatisTestUtils;
import org.col.db.mapper.SectorMapperTest;
import org.col.db.mapper.TreeMapper;
import org.junit.Test;

import static org.col.api.vocab.Datasets.DRAFT_COL;
import static org.junit.Assert.assertEquals;

public class SectorDaoTest extends DaoTestBase {
  
  @Test
  public void create() {
    MybatisTestUtils.populateDraftTree(session());
    
    SectorDao dao = new SectorDao(factory());
    Sector s = SectorMapperTest.create();
    s.getTarget().setId("t4");
    s.setDatasetKey(11);
    dao.create(s);
  
    s = SectorMapperTest.create();
    s.getTarget().setId("t5");
    s.setDatasetKey(12);
    dao.create(s);
  
    TreeMapper tm = session().getMapper(TreeMapper.class);
  
    TreeNode tn = tm.get(DRAFT_COL, "t4");
    assertEquals(2, (int) tn.getDatasetSectors().get(11));
  
    tn = tm.get(DRAFT_COL, "t5");
    assertEquals(1, (int) tn.getDatasetSectors().get(11));
    assertEquals(1, (int) tn.getDatasetSectors().get(12));

    tn = tm.get(DRAFT_COL, "t3");
    assertEquals(3, (int) tn.getDatasetSectors().get(11));
    assertEquals(1, (int) tn.getDatasetSectors().get(12));
  
    tn = tm.get(DRAFT_COL, "t2");
    assertEquals(3, (int) tn.getDatasetSectors().get(11));
    assertEquals(1, (int) tn.getDatasetSectors().get(12));
  
    tn = tm.get(DRAFT_COL, "t1");
    assertEquals(3, (int) tn.getDatasetSectors().get(11));
    assertEquals(1, (int) tn.getDatasetSectors().get(12));
  }
}