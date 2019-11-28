package life.catalogue.db.mapper;

import life.catalogue.api.model.Page;
import life.catalogue.api.search.SectorSearchRequest;
import org.apache.ibatis.exceptions.PersistenceException;
import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Sector;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.db.MybatisTestUtils;
import org.gbif.nameparser.api.NomCode;
import org.junit.Test;

import static life.catalogue.api.TestEntityGenerator.DATASET11;
import static org.junit.Assert.*;

public class SectorMapperTest extends CRUDTestBase<Integer, Sector, SectorMapper> {
  
  private static final int targetDatasetKey = Datasets.DRAFT_COL;
  private static final int subjectDatasetKey = DATASET11.getKey();
  
  public SectorMapperTest() {
    super(SectorMapper.class);
  }
  
  private void add2Sectors() {
    // create a few draft taxa to attach sectors to
    MybatisTestUtils.populateDraftTree(session());
    
    Sector s1 = createTestEntity(targetDatasetKey);
    s1.getSubject().setId(TestEntityGenerator.TAXON1.getId());
    s1.getTarget().setId("t4");
    mapper().create(s1);
  
    Sector s2 = createTestEntity(targetDatasetKey);
    mapper().create(s2);
    commit();
  }
  
  @Test
  public void getBySubject() {
    add2Sectors();
    assertNotNull(mapper().getBySubject(targetDatasetKey, subjectDatasetKey, TestEntityGenerator.TAXON1.getId()));
    assertNull(mapper().getBySubject(targetDatasetKey, subjectDatasetKey +1, TestEntityGenerator.TAXON1.getId()));
    assertNull(mapper().getBySubject(targetDatasetKey, subjectDatasetKey, TestEntityGenerator.TAXON1.getId()+"dfrtgzh"));
  }
  
  @Test
  public void listByTarget() {
    add2Sectors();
    assertEquals(1, mapper().listByTarget(targetDatasetKey,"t4").size());
    assertEquals(0, mapper().listByTarget(targetDatasetKey,"t32134").size());
  }

  @Test
  public void list() {
    add2Sectors();
    assertEquals(2, mapper().listByDataset(targetDatasetKey,subjectDatasetKey).size());
    assertEquals(0, mapper().listByDataset(targetDatasetKey,-432).size());
  }
  
  @Test
  public void brokenSubjects() {
    add2Sectors();

    SectorSearchRequest req = SectorSearchRequest.byDataset(targetDatasetKey,subjectDatasetKey);
    req.setBroken(true);
    req.setTarget(false);
    assertEquals(1, mapper().search(req, new Page()).size());
    
    req.setSubjectDatasetKey(543432);
    assertEquals(0, mapper().search(req, new Page()).size());
  }
  
  @Test
  public void brokenTargets() {
    add2Sectors();
  
    SectorSearchRequest req = SectorSearchRequest.byDataset(targetDatasetKey,subjectDatasetKey);
    req.setBroken(true);
    req.setTarget(true);
    assertEquals(1, mapper().search(req, new Page()).size());
  
    req.setSubjectDatasetKey(543432);
    assertEquals(0, mapper().search(req, new Page()).size());
  }
  
  @Test
  public void listTargetDatasetKeys() {
    assertEquals(0, mapper().listTargetDatasetKeys().size());
    add2Sectors();
    assertEquals(1, mapper().listTargetDatasetKeys().size());
  }
  
  @Override
  Sector createTestEntity(int dkey) {
    return create();
  }
  
  public static Sector create() {
    Sector d = new Sector();
    d.setDatasetKey(Datasets.DRAFT_COL);
    d.setSubjectDatasetKey(subjectDatasetKey);
    d.setMode(Sector.Mode.ATTACH);
    d.setCode(NomCode.ZOOLOGICAL);
    d.setSubject(TestEntityGenerator.newSimpleName());
    d.setTarget(TestEntityGenerator.newSimpleNameWithoutStatusParent());
    d.setNote(RandomUtils.randomUnicodeString(1024));
    d.setCreatedBy(TestEntityGenerator.USER_EDITOR.getKey());
    d.setModifiedBy(TestEntityGenerator.USER_EDITOR.getKey());
    return d;
  }
  
  @Override
  Sector removeDbCreatedProps(Sector s) {
    // remove newly set property
    s.setCreated(null);
    s.setModified(null);
    return s;
  }
  
  @Override
  void updateTestObj(Sector s) {
    s.setNote("not my thing");
  }
  
  @Test(expected = PersistenceException.class)
  public void unique() throws Exception {
    Sector d1 = create();
    mapper().create(d1);
    commit();
    
    d1.setKey(null);
    mapper().create(d1);
    commit();
  }
  
  @Test
  public void process(){
    // processing
    DecisionMapperTest.CountHandler handler = new DecisionMapperTest.CountHandler();
    mapper().processDataset(Datasets.DRAFT_COL, handler);
    assertEquals(0, handler.counter.size());
  }
}