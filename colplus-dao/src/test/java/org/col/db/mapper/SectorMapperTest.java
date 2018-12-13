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

public class SectorMapperTest extends CRUDIntMapperTest<Sector, SectorMapper> {
  
  private ColSource source;
  
  public SectorMapperTest() {
    super(SectorMapper.class);
  }
  
  @Before
  public void initSource() {
    source = ColSourceMapperTest.create(DATASET11.getKey());
    mapper(ColSourceMapper.class).create(source);
  }
  
  
  @Override
  Sector createTestEntity() {
    return create(source.getKey());
  }
  
  static Sector create(int sourceKey) {
    Sector d = new Sector();
    d.setColSourceKey(sourceKey);
    d.setMode(Sector.Mode.REPLACE);
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