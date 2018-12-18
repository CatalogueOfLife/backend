package org.col.db.mapper;

import org.col.api.TestEntityGenerator;
import org.col.api.model.ColSource;
import org.col.api.model.EditorialDecision;
import org.col.api.vocab.TaxonomicStatus;
import org.junit.Before;

import static org.col.api.TestEntityGenerator.DATASET11;
import static org.col.api.TestEntityGenerator.newNameRef;

public class DecisionMapperTest extends CRUDIntMapperTest<EditorialDecision, DecisionMapper> {
  
  private ColSource source;
  
  public DecisionMapperTest() {
    super(DecisionMapper.class);
  }
  
  @Before
  public void initSource() {
    source = ColSourceMapperTest.create(DATASET11.getKey());
    mapper(ColSourceMapper.class).create(source);
    
    commit();
  }
  
  
  @Override
  void updateTestObj(EditorialDecision ed) {
    ed.setNote("My next note");
    ed.setName(TestEntityGenerator.newName("updatedID"));
  }
  
  @Override
  EditorialDecision createTestEntity() {
    return create(source.getKey());
  }

  public static EditorialDecision create(int sourceKey) {
    EditorialDecision d = new EditorialDecision();
    d.setColSourceKey(sourceKey);
    d.setSubject(newNameRef());
    d.setMode(EditorialDecision.Mode.CREATE);
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