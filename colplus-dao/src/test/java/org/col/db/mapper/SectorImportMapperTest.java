package org.col.db.mapper;

import java.time.LocalDateTime;

import com.google.common.collect.Lists;
import org.col.api.RandomUtils;
import org.col.api.TestEntityGenerator;
import org.col.api.model.Page;
import org.col.api.model.Sector;
import org.col.api.model.SectorImport;
import org.junit.Before;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.*;
import static org.junit.Assert.assertEquals;

public class SectorImportMapperTest extends MapperTestBase<SectorImportMapper> {
  int attempts = 1;
  
  Sector s;
  Sector s2;

  @Before
  public void prepare() {
    s = new Sector();
    s.setDatasetKey(DATASET11.getKey());
    s.setMode(Sector.Mode.ATTACH);
    s.setSubject(newSimpleName());
    s.setTarget(newSimpleName());
    s.setNote(RandomUtils.randomUnicodeString(1024));
    s.setCreatedBy(TestEntityGenerator.USER_EDITOR.getKey());
    s.setModifiedBy(TestEntityGenerator.USER_EDITOR.getKey());
    
    mapper(SectorMapper.class).create(s);
  
    s2 = new Sector();
    s2.setDatasetKey(DATASET12.getKey());
    s2.setMode(Sector.Mode.ATTACH);
    s2.setSubject(newSimpleName());
    s2.setTarget(newSimpleName());
    s2.setNote(RandomUtils.randomUnicodeString(1024));
    s2.setCreatedBy(TestEntityGenerator.USER_EDITOR.getKey());
    s2.setModifiedBy(TestEntityGenerator.USER_EDITOR.getKey());
  
    mapper(SectorMapper.class).create(s2);
  }
  
  private SectorImport create(SectorImport.State state, Sector s) throws Exception {
    SectorImport d = new SectorImport();
    d.setType(getClass().getSimpleName());
    d.setSectorKey(s.getKey());
    d.setAttempt(attempts++);
    d.setError("no error");
    d.addWarning("warning 1");
    d.addWarning("warning 2");
    d.setState(state);
    d.setStarted(LocalDateTime.now());
    d.setFinished(LocalDateTime.now());
    d.setNameCount(65432);
    d.setTaxonCount(5748329);
    d.setReferenceCount(9781);
    d.setDistributionCount(12345);
    d.setVernacularCount(432);
    return d;
  }

  public SectorImportMapperTest() {
    super(SectorImportMapper.class);
  }
  
  
  @Test
  public void roundtrip() throws Exception {
    SectorImport d1 = create(SectorImport.State.FINISHED, s);
    mapper().create(d1);
    commit();
    assertEquals(1, d1.getAttempt());
  
    SectorImport d2 = mapper().get(d1.getSectorKey(), d1.getAttempt());
    assertEquals(d1, d2);
  }
  
  @Test
  public void listCount() throws Exception {
    mapper().create(create(SectorImport.State.FAILED, s));
    mapper().create(create(SectorImport.State.FINISHED, s));
    mapper().create(create(SectorImport.State.PREPARING, s));
    mapper().create(create(SectorImport.State.FINISHED, s));
    mapper().create(create(SectorImport.State.CANCELED, s));
    mapper().create(create(SectorImport.State.COPYING, s2));
    mapper().create(create(SectorImport.State.FINISHED, s2));
    
    assertEquals(7, mapper().count(null, null, null));
    assertEquals(7, mapper().count(null,null, Lists.newArrayList()));
    assertEquals(1, mapper().count(null,null, Lists.newArrayList(SectorImport.State.FAILED)));
    assertEquals(3, mapper().count(null,null, Lists.newArrayList(SectorImport.State.FINISHED)));
    assertEquals(2, mapper().count(null,null, Lists.newArrayList(SectorImport.State.COPYING, SectorImport.State.PREPARING)));
    
    assertEquals(2, mapper().list(null,null, Lists.newArrayList(SectorImport.State.COPYING, SectorImport.State.PREPARING), new Page()).size());
  
    assertEquals(5, mapper().count(s.getKey(), null, null));
    assertEquals(5, mapper().count(s.getKey(), s.getDatasetKey(), null));
    assertEquals(0, mapper().count(s2.getKey(), s.getDatasetKey(), null));
    assertEquals(5, mapper().count(null, s.getDatasetKey(), null));
    assertEquals(2, mapper().count(null, s2.getDatasetKey(), null));
    assertEquals(2, mapper().count(s2.getKey(), null, null));
    
    assertEquals(0, mapper().count(99999, null, null));
    assertEquals(0, mapper().count(99999, 789, null));
    
  }
  
}