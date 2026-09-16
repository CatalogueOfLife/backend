package life.catalogue.dao;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.Sector;
import life.catalogue.api.search.SectorSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.SectorMapperTest;
import life.catalogue.es.indexing.NameUsageIndexService;
import life.catalogue.img.ThumborConfig;
import life.catalogue.img.ThumborService;
import life.catalogue.junit.MybatisTestUtils;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.matching.nidx.NameIndexFactory;

import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class SectorDaoTest extends DaoTestBase {
  static int user = TestEntityGenerator.USER_EDITOR.getKey();

  final int subjectDatasetKey = TestEntityGenerator.DATASET11.getKey();

  SectorDao dao;

  @Before
  public void init(){
    NameDao nDao = new NameDao(SqlSessionFactoryRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), NameIndexFactory.passThru(), validator);
    TaxonDao tDao = new TaxonDao(SqlSessionFactoryRule.getSqlSessionFactory(), nDao, null, new ThumborService(new ThumborConfig()), NameUsageIndexService.passThru(), null, validator);
    dao = new SectorDao(factory(), NameUsageIndexService.passThru(), tDao, validator);
  }

  @Test
  public void resetCreate() {

    try (SqlSession session = factory().openSession(true)) {
      MybatisTestUtils.populateDraftTree(session);
      MybatisTestUtils.populateTestTree(12, session);
    }

    setupSectors(dao);
  }

  @Test
  public void updateRedoPrios() {

    try (SqlSession session = factory().openSession(true)) {
      MybatisTestUtils.populateDraftTree(session);
      MybatisTestUtils.populateTestTree(12, session);
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
   * Subject and target are a copy of their linked usages. A client can change the linked id, but never the stored
   * name - the sector form used to send the full label with authorship as the name.
   */
  @Test
  public void updateCopiesLinkedNames() {

    try (SqlSession session = factory().openSession(true)) {
      MybatisTestUtils.populateDraftTree(session);
      MybatisTestUtils.populateTestTree(12, session);
    }

    Sector s = SectorMapperTest.create();
    s.getSubject().setId("root-1");
    s.getTarget().setId("t4"); // Coleoptera
    dao.create(s, user);
    final String subjectName = dao.get(s).getSubject().getName();

    // a new target id sent with a label as its name
    s = dao.get(s);
    s.getSubject().setName(subjectName + " Mill.");
    s.getTarget().setId("t1"); // Animalia
    s.getTarget().setName("Animalia Linnaeus, 1758");
    s.getTarget().setAuthorship(null);
    dao.update(s, user);

    var read = dao.get(s);
    assertEquals("an unchanged id keeps the stored name", subjectName, read.getSubject().getName());
    assertEquals("t1", read.getTarget().getId());
    assertEquals("a new id copies the name from its usage", "Animalia", read.getTarget().getName());
  }

  /**
   * Sectors may share a subject, including several subject less merge sectors from the same source.
   * They are reported as potential duplicates instead. Only the hierarchy sector must remain the only one in a project.
   * See https://github.com/CatalogueOfLife/backend/issues/1560 and https://github.com/CatalogueOfLife/backend/issues/1581
   */
  @Test
  public void createSectorsSharingSubject() {

    try (SqlSession session = factory().openSession(true)) {
      MybatisTestUtils.populateDraftTree(session);
      MybatisTestUtils.populateTestTree(12, session);
    }

    // an attach sector and a vernacular only merge sector on the same subject
    Sector attach = SectorMapperTest.create();
    attach.setSubjectDatasetKey(12);
    attach.getSubject().setId("t2");
    attach.getTarget().setId("t1"); // Animalia
    attach.setMode(Sector.Mode.ATTACH);
    dao.create(attach, user);

    Sector vernacular = mergeSector(12, "t2");
    vernacular.setEntities(Set.of(EntityType.VERNACULAR));
    dao.create(vernacular, user);

    // merge sectors with another subject are no duplicates
    dao.create(mergeSector(12, "t3"), user);

    // several subject less merge sectors from the same source
    dao.create(mergeSector(12, null), user);
    dao.create(mergeSector(12, null), user);
    // another source dataset can have its own subject less merge sector
    dao.create(mergeSector(11, null), user);

    var req = SectorSearchRequest.byProject(Datasets.COL);
    req.setDuplicates(true);
    assertEquals(4, dao.search(req, new Page()).getTotal());
    var groups = dao.duplicates(req, new Page());
    assertEquals(2, groups.getTotal());
    assertNull(groups.getResult().get(0).getSubjectId());
    assertEquals(2, groups.getResult().get(0).getSectors().size());
    assertEquals("t2", groups.getResult().get(1).getSubjectId());
    assertEquals(List.of(attach.getId(), vernacular.getId()), groups.getResult().get(1).getSectors().stream().map(Sector::getId).toList());

    // there is only one hierarchy sector per project
    dao.create(hierarchySector(), user);
    try {
      dao.create(hierarchySector(), user);
      fail("A second hierarchy sector must not be allowed");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  static Sector hierarchySector() {
    Sector s = mergeSector(12, null);
    s.setMode(Sector.Mode.HIERARCHY);
    return s;
  }

  static Sector mergeSector(int subjectDatasetKey, @Nullable String subjectID) {
    Sector s = SectorMapperTest.create();
    s.setMode(Sector.Mode.MERGE);
    s.setPriority(null);
    s.setTarget(null);
    s.setSubjectDatasetKey(subjectDatasetKey);
    if (subjectID == null) {
      s.setSubject(null);
    } else {
      s.getSubject().setId(subjectID);
    }
    return s;
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
    }

    Sector s = SectorMapperTest.create();
    s.setSubjectDatasetKey(11);
    s.getSubject().setId("root-1");
    s.getTarget().setId("t4");
    dao.create(s, user);

    dao.deleteSector(s, false);
  }

}