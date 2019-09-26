package org.col.db.mapper;

import org.apache.ibatis.exceptions.PersistenceException;
import org.col.api.RandomUtils;
import org.col.api.TestEntityGenerator;
import org.col.api.model.Sector;
import org.col.api.vocab.Datasets;
import org.col.db.MybatisTestUtils;
import org.gbif.nameparser.api.NomCode;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.DATASET11;
import static org.junit.Assert.*;

public class SectorMapperTest extends GlobalCRUDMapperTest<Sector, SectorMapper> {
  
  private static final int targetDatasetKey = Datasets.DRAFT_COL;
  private static final int subjectDatasetKey = DATASET11.getKey();
  
  public SectorMapperTest() {
    super(SectorMapper.class);
  }
  
  private void add2Sectors() {
    // create a few draft taxa to attach sectors to
    MybatisTestUtils.populateDraftTree(session());
    
    Sector s1 = createTestEntity();
    s1.getSubject().setId(TestEntityGenerator.TAXON1.getId());
    s1.getTarget().setId("t4");
    mapper().create(s1);
  
    Sector s2 = createTestEntity();
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
    assertEquals(1, mapper().subjectBroken(targetDatasetKey,subjectDatasetKey).size());
    assertEquals(0, mapper().subjectBroken(targetDatasetKey,543432).size());
  }
  
  @Test
  public void brokenTargets() {
    add2Sectors();
    assertEquals(1, mapper().targetBroken(targetDatasetKey, subjectDatasetKey).size());
    assertEquals(0, mapper().targetBroken(targetDatasetKey,543432).size());
  }
  
  @Override
  Sector createTestEntity() {
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
  
}