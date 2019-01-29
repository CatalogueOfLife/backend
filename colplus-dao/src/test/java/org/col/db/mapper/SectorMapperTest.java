package org.col.db.mapper;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.ibatis.exceptions.PersistenceException;
import org.col.api.TestEntityGenerator;
import org.col.api.model.ColSource;
import org.col.api.model.Sector;
import org.junit.Before;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.DATASET11;
import static org.col.api.TestEntityGenerator.newNameRef;
import static org.junit.Assert.assertEquals;

public class SectorMapperTest extends CRUDIntMapperTest<Sector, SectorMapper> {
  
  private static final int datasetKey = DATASET11.getKey();
  private ColSource source;
  
  public SectorMapperTest() {
    super(SectorMapper.class);
  }
  
  @Before
  public void initSource() {
    source = ColSourceMapperTest.create(datasetKey);
    mapper(ColSourceMapper.class).create(source);
  }
  
  private void add2Sectors() {
    // create a few draft taxa to attach sectors to
    populateDraftTree();
    
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
    assertEquals(2, mapper().list(datasetKey, null).size());
    assertEquals(2, mapper().list(datasetKey, source.getKey()).size());
    assertEquals(2, mapper().list(null, source.getKey()).size());
    assertEquals(2, mapper().list(null, null).size());
    assertEquals(0, mapper().list(datasetKey, -8765).size());
    assertEquals(0, mapper().list(null, -8765).size());
    assertEquals(0, mapper().list(-432, null).size());
  }
  
  @Test
  public void brokenSubjects() {
    add2Sectors();
    assertEquals(1, mapper().subjectBroken(datasetKey, null).size());
    assertEquals(1, mapper().subjectBroken(datasetKey, source.getKey()).size());
    assertEquals(0, mapper().subjectBroken(datasetKey, -8765).size());
    assertEquals(0, mapper().subjectBroken(543432, null).size());
  }
  
  @Test
  public void brokenTargets() {
    add2Sectors();
    assertEquals(1, mapper().targetBroken(datasetKey, null).size());
    assertEquals(1, mapper().targetBroken(datasetKey, source.getKey()).size());
    assertEquals(0, mapper().targetBroken(datasetKey, -8765).size());
    assertEquals(0, mapper().targetBroken(543432, null).size());
  }
  
  @Override
  Sector createTestEntity() {
    return create(source.getKey());
  }
  
  static Sector create(Integer sourceKey) {
    Sector d = new Sector();
    d.setDatasetKey(datasetKey);
    d.setColSourceKey(sourceKey);
    d.setMode(Sector.Mode.ATTACH);
    d.setSubject(newNameRef());
    d.setTarget(newNameRef());
    d.setNote(RandomStringUtils.random(1024));
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
    Sector d1 = create(source.getKey());
    mapper().create(d1);
    commit();
    
    d1.setKey(null);
    mapper().create(d1);
    commit();
  }
  
}