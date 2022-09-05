package life.catalogue.dao;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.search.SectorSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.MybatisTestUtils;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.SectorMapperTest;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.db.mapper.TreeMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.matching.NameIndexFactory;

import org.apache.ibatis.session.SqlSession;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class SectorDaoTest extends DaoTestBase {
  static int user = TestEntityGenerator.USER_EDITOR.getKey();

  final int subjectDatasetKey = TestEntityGenerator.DATASET11.getKey();

  SectorDao dao;

  @Before
  public void init(){
    NameDao nDao = new NameDao(PgSetupRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), NameIndexFactory.passThru(), validator);
    TaxonDao tDao = new TaxonDao(PgSetupRule.getSqlSessionFactory(), nDao, NameUsageIndexService.passThru(), validator);
    dao = new SectorDao(factory(), NameUsageIndexService.passThru(), tDao, validator);
  }

  @Test
  public void resetCreate() {

    try (SqlSession session = factory().openSession(true)) {
      MybatisTestUtils.populateDraftTree(session);
      MybatisTestUtils.populateTestTree(12, session);
      
      TaxonMapper txm = session.getMapper(TaxonMapper.class);
      txm.resetDatasetSectorCount(3);
      session.commit();
    }

    setupSectors(dao);

    assertSectorCounts();
  }

  @Test
  public void updateRedoPrios() {

    try (SqlSession session = factory().openSession(true)) {
      MybatisTestUtils.populateDraftTree(session);
      MybatisTestUtils.populateTestTree(12, session);

      TaxonMapper txm = session.getMapper(TaxonMapper.class);
      txm.resetDatasetSectorCount(3);
      session.commit();
    }

    Sector s = SectorMapperTest.create();
    s.getSubject().setId("root-1");
    s.getTarget().setId("t4"); // Coleoptera
    dao.create(s, user);

    var s2 = dao.get(s);
    assertNull(s.getPriority());

    s.setPriority(100);
    dao.update(s,user);
    assertEquals(100, (int) s.getPriority());


    s2 = SectorMapperTest.create();
    s2.setSubjectDatasetKey(12);
    s2.getSubject().setId("t2");
    s2.getTarget().setId("t1"); // Animalia
    dao.create(s2, user);
    s2.setPriority(100);
    dao.update(s2,user);

    var s3 = dao.get(s2);
    assertEquals(100, (int) s3.getPriority());
    s3 = dao.get(s);
    assertEquals(101, (int) s3.getPriority());

  }

  /**
   * 3 sectors, 2 from source 12, 1 from 11
   */
  static void setupSectors(SectorDao dao) {
    // now create some sectors and test again
    Sector s = SectorMapperTest.create();
    s.setSubjectDatasetKey(11);
    s.getSubject().setId("root-1");
    s.getTarget().setId("t4"); // Coleoptera
    s.setMode(Sector.Mode.ATTACH);
    dao.create(s, user);

    s = SectorMapperTest.create();
    s.setSubjectDatasetKey(12);
    s.getSubject().setId("t2");
    s.getTarget().setId("t1"); // Animalia
    s.setMode(Sector.Mode.UNION);
    dao.create(s, user);

    s = SectorMapperTest.create();
    s.setSubjectDatasetKey(12);
    s.getSubject().setId("t3");
    s.getTarget().setId("t3"); // Insecta
    s.setMode(Sector.Mode.ATTACH);
    dao.create(s, user);
  }
  static void assertSectorCounts() {
    try (SqlSession session = factory().openSession(true)) {
      TreeMapper tm = session.getMapper(TreeMapper.class);

      TreeNode tn = tm.get(Datasets.COL, TreeNode.Type.CATALOGUE, DSID.colID("t5"));
      assertNull(tn.getDatasetSectors());

      tn = tm.get(Datasets.COL, TreeNode.Type.CATALOGUE, DSID.colID("t4"));
      assertEquals(1, tn.getDatasetSectors().get(11));
      assertFalse(tn.getDatasetSectors().containsKey(12));

      tn = tm.get(Datasets.COL, TreeNode.Type.CATALOGUE, DSID.colID("t3"));
      assertEquals(1, tn.getDatasetSectors().get(11));
      assertEquals(1, tn.getDatasetSectors().get(12));

      tn = tm.get(Datasets.COL, TreeNode.Type.CATALOGUE, DSID.colID("t2"));
      assertEquals(1, tn.getDatasetSectors().get(11));
      assertEquals(1, tn.getDatasetSectors().get(12));

      tn = tm.get(Datasets.COL, TreeNode.Type.CATALOGUE, DSID.colID("t1"));
      assertEquals(1, tn.getDatasetSectors().get(11));
      assertEquals(2, tn.getDatasetSectors().get(12));
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void validate() {
    SectorSearchRequest req = new SectorSearchRequest();
    req.setWithoutData(true);
    dao.search(req, new Page()).size();
  }

  @Test(expected = IllegalArgumentException.class)
  public void failNotManaged() {
    Dataset d = TestEntityGenerator.newDataset("grr");
    try (SqlSession session = factory().openSession(true)) {
      // create a dataset which is not managed
      d.setKey(999);
      d.setOrigin(DatasetOrigin.EXTERNAL);
      d.applyUser(Users.TESTER);
      session.getMapper(DatasetMapper.class).create(d);
    }

    Sector s = new Sector();
    s.setDatasetKey(d.getKey());
    s.setTarget(TestEntityGenerator.newSimpleName("x"));
    s.setSubjectDatasetKey(subjectDatasetKey);
    s.setSubject(TestEntityGenerator.newSimpleName("root-1"));
    // this should fail with IAE!
    dao.create(s, Users.TESTER);
  }

  @Test(expected = IllegalArgumentException.class)
  public void failBadTarget() {
    Sector s = new Sector();
    s.setDatasetKey(Datasets.COL);
    s.setTarget(TestEntityGenerator.newSimpleName("x"));
    s.setSubjectDatasetKey(subjectDatasetKey);
    s.setSubject(TestEntityGenerator.newSimpleName("x"));
    // this should fail with IAE!
    dao.create(s, Users.TESTER);
  }

  @Test(expected = UnsupportedOperationException.class)
  public void delete() {
    dao.delete(SectorMapperTest.create(), Users.TESTER);
  }

  @Test
  public void deleteSector() {

    try (SqlSession session = factory().openSession(true)) {
      MybatisTestUtils.populateDraftTree(session);
      MybatisTestUtils.populateTestTree(12, session);

      TaxonMapper txm = session.getMapper(TaxonMapper.class);
      txm.resetDatasetSectorCount(3);
      session.commit();
    }

    Sector s = SectorMapperTest.create();
    s.setSubjectDatasetKey(11);
    s.getSubject().setId("root-1");
    s.getTarget().setId("t4");
    dao.create(s, user);

    dao.deleteSector(s, false);
  }

}