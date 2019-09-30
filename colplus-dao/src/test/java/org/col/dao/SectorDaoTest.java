package org.col.dao;

import org.apache.ibatis.session.SqlSession;
import org.col.api.TestEntityGenerator;
import org.col.api.model.Sector;
import org.col.api.model.TreeNode;
import org.col.db.MybatisTestUtils;
import org.col.db.mapper.SectorMapperTest;
import org.col.db.mapper.TaxonMapper;
import org.col.db.mapper.TreeMapper;
import org.junit.Test;

import static org.col.api.vocab.Datasets.DRAFT_COL;
import static org.junit.Assert.assertEquals;

public class SectorDaoTest extends DaoTestBase {
  static int user = TestEntityGenerator.USER_EDITOR.getKey();

  @Test
  public void resetCreate() {

    try (SqlSession session = factory().openSession(true)) {
      MybatisTestUtils.populateDraftTree(session);
      MybatisTestUtils.populateTestTree(12, session);
      
      TaxonMapper txm = session.getMapper(TaxonMapper.class);
      txm.resetDatasetSectorCount(3);
      session.commit();
    }
  
    SectorDao dao = new SectorDao(factory());
    Sector s = SectorMapperTest.create();
    s.setDatasetKey(11);
    s.getSubject().setId("root-1");
    s.getTarget().setId("t4");
    dao.create(s, user);
  
    s = SectorMapperTest.create();
    s.setDatasetKey(12);
    s.getSubject().setId("t2");
    s.getTarget().setId("t5");
    dao.create(s, user);
  
    s = SectorMapperTest.create();
    s.setDatasetKey(12);
    s.getSubject().setId("t3");
    s.getTarget().setId("t3");
    dao.create(s, user);
  
  
    try (SqlSession session = factory().openSession(true)) {
      TreeMapper tm = session.getMapper(TreeMapper.class);
    
      TreeNode tn = tm.get(DRAFT_COL, "t5");
      assertEquals(0, (int) tn.getDatasetSectors().get(11));
      assertEquals(1, (int) tn.getDatasetSectors().get(12));
  
      tn = tm.get(DRAFT_COL, "t4");
      assertEquals(1, (int) tn.getDatasetSectors().get(11));
      assertEquals(0, (int) tn.getDatasetSectors().get(12));

      tn = tm.get(DRAFT_COL, "t3");
      assertEquals(1, (int) tn.getDatasetSectors().get(11));
      assertEquals(2, (int) tn.getDatasetSectors().get(12));
    
      tn = tm.get(DRAFT_COL, "t2");
      assertEquals(1, (int) tn.getDatasetSectors().get(11));
      assertEquals(2, (int) tn.getDatasetSectors().get(12));
    
      tn = tm.get(DRAFT_COL, "t1");
      assertEquals(1, (int) tn.getDatasetSectors().get(11));
      assertEquals(2, (int) tn.getDatasetSectors().get(12));
    }
  }
  
}