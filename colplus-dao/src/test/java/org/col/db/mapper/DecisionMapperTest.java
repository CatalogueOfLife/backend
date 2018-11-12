package org.col.db.mapper;

import org.col.api.RandomUtils;
import org.col.api.model.ColSource;
import org.col.api.model.EditorialDecision;
import org.col.api.model.Sector;
import org.col.api.vocab.TaxonomicStatus;
import org.junit.Before;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.DATASET11;
import static org.col.api.TestEntityGenerator.newNameRef;
import static org.junit.Assert.*;

public class DecisionMapperTest extends MapperTestBase<DecisionMapper> {
  
  private ColSource source;
  private Sector sector;
  
  public DecisionMapperTest() {
    super(DecisionMapper.class);
  }
  
  @Before
  public void initSource() {
    source = ColSourceMapperTest.create(DATASET11.getKey());
    mapper(ColSourceMapper.class).create(source);
    
    sector = SectorMapperTest.create(source.getKey());
    mapper(SectorMapper.class).create(sector);
    
    commit();
  }
  
  @Test
  public void roundtrip() throws Exception {
    EditorialDecision d1 = create(sector.getKey());
    mapper().create(d1);
    
    commit();
    
    EditorialDecision d2 = mapper().get(d1.getKey());
    // remove newly set property
    d2.setCreated(null);
    
    assertEquals(d1, d2);
  }
  
  @Test
  public void delete() throws Exception {
    EditorialDecision d1 = create(sector.getKey());
    mapper().create(d1);
    
    commit();
    
    // not deleted yet
    EditorialDecision d = mapper().get(d1.getKey());
    assertNotNull(d.getCreated());
    
    // physically delete
    mapper().delete(d1.getKey());
    assertNull(mapper().get(d1.getKey()));
  }
  
  public static EditorialDecision create(int sectorKey) throws Exception {
    EditorialDecision d = new EditorialDecision();
    d.setSectorKey(sectorKey);
    d.setSubject(newNameRef());
    d.setName(RandomUtils.randomSpecies());
    d.setAuthorship(RandomUtils.randomAuthorship().toString());
    d.setStatus(TaxonomicStatus.AMBIGUOUS_SYNONYM);
    return d;
  }
  
}