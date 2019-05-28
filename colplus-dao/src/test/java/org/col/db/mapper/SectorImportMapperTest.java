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

import static org.col.api.TestEntityGenerator.DATASET11;
import static org.col.api.TestEntityGenerator.newNameRef;
import static org.junit.Assert.assertEquals;

public class SectorImportMapperTest extends MapperTestBase<SectorImportMapper> {
  int attempts = 1;
  
  Sector s;
  @Before
  public void prepare() {
    s = new Sector();
    s.setDatasetKey(DATASET11.getKey());
    s.setMode(Sector.Mode.ATTACH);
    s.setSubject(newNameRef());
    s.setTarget(newNameRef());
    s.setNote(RandomUtils.randomUnicodeString(1024));
    s.setCreatedBy(TestEntityGenerator.USER_EDITOR.getKey());
    s.setModifiedBy(TestEntityGenerator.USER_EDITOR.getKey());
    
    mapper(SectorMapper.class).create(s);
  }
  
  private SectorImport create(SectorImport.State state) throws Exception {
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
    SectorImport d1 = create(SectorImport.State.FINISHED);
    mapper().create(d1);
    commit();
    assertEquals(1, d1.getAttempt());
  
    SectorImport d2 = mapper().get(d1.getSectorKey(), d1.getAttempt());
    assertEquals(d1, d2);
  }
  
  @Test
  public void listCount() throws Exception {
    mapper().create(create(SectorImport.State.FAILED));
    mapper().create(create(SectorImport.State.FINISHED));
    mapper().create(create(SectorImport.State.PREPARING));
    mapper().create(create(SectorImport.State.FINISHED));
    mapper().create(create(SectorImport.State.CANCELED));
    mapper().create(create(SectorImport.State.COPYING));
    mapper().create(create(SectorImport.State.FINISHED));
    
    assertEquals(7, mapper().count(null, null));
    assertEquals(7, mapper().count(null, Lists.newArrayList()));
    assertEquals(1, mapper().count(null, Lists.newArrayList(SectorImport.State.FAILED)));
    assertEquals(3, mapper().count(null, Lists.newArrayList(SectorImport.State.FINISHED)));
    assertEquals(2, mapper().count(null, Lists.newArrayList(SectorImport.State.COPYING, SectorImport.State.PREPARING)));
    
    assertEquals(2, mapper().list(null, Lists.newArrayList(SectorImport.State.COPYING, SectorImport.State.PREPARING), new Page()).size());
  }
  
}