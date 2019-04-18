package org.col.db.mapper;

import org.apache.ibatis.exceptions.PersistenceException;
import org.col.api.RandomUtils;
import org.col.api.TestEntityGenerator;
import org.col.api.model.Sector;
import org.col.db.MybatisTestUtils;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.DATASET11;
import static org.col.api.TestEntityGenerator.newNameRef;
import static org.junit.Assert.assertEquals;

public class SectorMapperTest extends GlobalCRUDMapperTest<Sector, SectorMapper> {
  
  private static final int datasetKey = DATASET11.getKey();
  
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
  public void list() {
    add2Sectors();
    assertEquals(2, mapper().listByDataset(datasetKey).size());
    assertEquals(2, mapper().listByDataset(null).size());
    assertEquals(0, mapper().listByDataset(-432).size());
  }
  
  @Test
  public void brokenSubjects() {
    add2Sectors();
    assertEquals(1, mapper().subjectBroken(datasetKey).size());
    assertEquals(0, mapper().subjectBroken(543432).size());
  }
  
  @Test
  public void brokenTargets() {
    add2Sectors();
    assertEquals(1, mapper().targetBroken(datasetKey).size());
    assertEquals(0, mapper().targetBroken(543432).size());
  }
  
  @Override
  Sector createTestEntity() {
    return create();
  }
  
  public static Sector create() {
    Sector d = new Sector();
    d.setDatasetKey(datasetKey);
    d.setMode(Sector.Mode.ATTACH);
    d.setSubject(newNameRef());
    d.setTarget(newNameRef());
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