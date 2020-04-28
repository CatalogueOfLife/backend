package life.catalogue.dao;

import life.catalogue.es.NameUsageIndexService;
import org.apache.ibatis.session.SqlSession;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.TreeNode;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.db.MybatisTestUtils;
import life.catalogue.db.mapper.SectorMapperTest;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.db.mapper.TreeMapper;
import org.junit.Test;

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
  
    SectorDao dao = new SectorDao(factory(), NameUsageIndexService.passThru());
    Sector s = SectorMapperTest.create();
    s.setSubjectDatasetKey(11);
    s.getSubject().setId("root-1");
    s.getTarget().setId("t4");
    dao.create(s, user);
  
    s = SectorMapperTest.create();
    s.setSubjectDatasetKey(12);
    s.getSubject().setId("t2");
    s.getTarget().setId("t5");
    dao.create(s, user);
  
    s = SectorMapperTest.create();
    s.setSubjectDatasetKey(12);
    s.getSubject().setId("t3");
    s.getTarget().setId("t3");
    dao.create(s, user);
  
  
    try (SqlSession session = factory().openSession(true)) {
      TreeMapper tm = session.getMapper(TreeMapper.class);
    
      TreeNode tn = tm.get(Datasets.DRAFT_COL, TreeNode.Type.CATALOGUE, DSID.draftID("t5"));
      assertEquals(0, tn.getDatasetSectors().get(11));
      assertEquals(1, tn.getDatasetSectors().get(12));
  
      tn = tm.get(Datasets.DRAFT_COL, TreeNode.Type.CATALOGUE, DSID.draftID("t4"));
      assertEquals(1, tn.getDatasetSectors().get(11));
      assertEquals(0, tn.getDatasetSectors().get(12));

      tn = tm.get(Datasets.DRAFT_COL, TreeNode.Type.CATALOGUE, DSID.draftID("t3"));
      assertEquals(1, tn.getDatasetSectors().get(11));
      assertEquals(2, tn.getDatasetSectors().get(12));
    
      tn = tm.get(Datasets.DRAFT_COL, TreeNode.Type.CATALOGUE, DSID.draftID("t2"));
      assertEquals(1, tn.getDatasetSectors().get(11));
      assertEquals(2, tn.getDatasetSectors().get(12));
    
      tn = tm.get(Datasets.DRAFT_COL, TreeNode.Type.CATALOGUE, DSID.draftID("t1"));
      assertEquals(1, tn.getDatasetSectors().get(11));
      assertEquals(2, tn.getDatasetSectors().get(12));
    }
  }
  
}