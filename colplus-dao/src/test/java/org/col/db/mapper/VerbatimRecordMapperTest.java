package org.col.db.mapper;

import org.col.api.TestEntityGenerator;
import org.col.api.model.Page;
import org.col.api.model.VerbatimRecord;
import org.col.api.vocab.Issue;
import org.gbif.dwc.terms.AcefTerm;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.DATASET11;
import static org.col.api.TestEntityGenerator.TAXON1;
import static org.junit.Assert.assertEquals;

/**
 *
 */
public class VerbatimRecordMapperTest extends MapperTestBase<VerbatimRecordMapper> {
  
  public VerbatimRecordMapperTest() {
    super(VerbatimRecordMapper.class);
  }
  
  @Test
  public void roundtrip() {
    VerbatimRecord r1 = TestEntityGenerator.createVerbatim();
    mapper().create(r1);
    
    commit();
    
    VerbatimRecord r2 = mapper().get(r1.getDatasetKey(), r1.getKey());
    
    assertEquals(r1, r2);
  }
  
  @Test
  public void list() {
    assertEquals(0, mapper().list(TAXON1.getDatasetKey(), null, Issue.ACCEPTED_ID_INVALID, new Page()).size());
    assertEquals(1, mapper().list(TAXON1.getDatasetKey(), null, Issue.ID_NOT_UNIQUE, new Page()).size());
  }
  
  @Test
  public void count() {
    // count apples. rely on import metrics for quick counts so derive them first
    generateDatasetImport(DATASET11.getKey());
    assertEquals(5, mapper().count(DATASET11.getKey(), null));
    assertEquals(3, mapper().count(DATASET11.getKey(), AcefTerm.AcceptedSpecies));
    assertEquals(0, mapper().count(DATASET11.getKey(), AcefTerm.AcceptedInfraSpecificTaxa));
  }
  
}