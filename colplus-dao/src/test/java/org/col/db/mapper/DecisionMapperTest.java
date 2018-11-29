package org.col.db.mapper;

import org.col.api.TestEntityGenerator;
import org.col.api.model.ColSource;
import org.col.api.model.EditorialDecision;
import org.col.api.model.Sector;
import org.col.api.vocab.TaxonomicStatus;
import org.junit.Before;

import static org.col.api.TestEntityGenerator.DATASET11;
import static org.col.api.TestEntityGenerator.newNameRef;

public class DecisionMapperTest extends CRUDMapperTest<EditorialDecision, DecisionMapper> {
  
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
  
  
  @Override
  void updateTestObj(EditorialDecision ed) {
    ed.setNote("My next note");
    ed.setName(TestEntityGenerator.newName("updatedID"));
  }
  
  @Override
  EditorialDecision createTestEntity() {
    return create(sector.getKey());
  }

  public static EditorialDecision create(int sectorKey) {
    EditorialDecision d = new EditorialDecision();
    d.setSectorKey(sectorKey);
    d.setSubject(newNameRef());
    d.setBlocked(false);
    d.setName(TestEntityGenerator.newName());
    d.setStatus(TaxonomicStatus.AMBIGUOUS_SYNONYM);
    d.setNote("I cannot remember why I did this.");
    
    d.setCreatedBy(TestEntityGenerator.USER_EDITOR.getKey());
    d.setModifiedBy(d.getCreatedBy());
    return d;
  }
  
  @Override
  EditorialDecision removeDbCreatedProps(EditorialDecision obj) {
    obj.setCreated(null);
    obj.setModified(null);
    return obj;
  }
  
}