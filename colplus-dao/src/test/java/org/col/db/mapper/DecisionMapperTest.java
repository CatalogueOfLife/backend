package org.col.db.mapper;

import org.col.api.TestEntityGenerator;
import org.col.api.model.EditorialDecision;
import org.col.api.vocab.Lifezone;
import org.col.api.vocab.TaxonomicStatus;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.DATASET11;
import static org.junit.Assert.assertEquals;

public class DecisionMapperTest extends GlobalCRUDMapperTest<EditorialDecision, DecisionMapper> {
  
  public DecisionMapperTest() {
    super(DecisionMapper.class);
  }
  
  final int datasetKey = DATASET11.getKey();
  
  @Test
  public void brokenDecisions() {
    EditorialDecision d1 = createTestEntity();
    d1.getSubject().setId(TestEntityGenerator.TAXON1.getId());
    mapper().create(d1);

    EditorialDecision d2 = createTestEntity();
    mapper().create(d2);
    commit();
    
    assertEquals(2, mapper().listByDataset(null, null).size());
    assertEquals(2, mapper().listByDataset(datasetKey, null).size());
    assertEquals(1, mapper().listByDataset(datasetKey, TestEntityGenerator.TAXON1.getId()).size());
    assertEquals(1, mapper().subjectBroken(datasetKey).size());
  }
  
  @Override
  void updateTestObj(EditorialDecision ed) {
    ed.setNote("My next note");
    ed.setName(TestEntityGenerator.newName("updatedID"));
  }
  
  @Override
  EditorialDecision createTestEntity() {
    return create(datasetKey);
  }

  public static EditorialDecision create(int datasetKey) {
    EditorialDecision d = new EditorialDecision();
    d.setDatasetKey(datasetKey);
    d.setSubject(TestEntityGenerator.newSimpleName());
    d.setMode(EditorialDecision.Mode.UPDATE);
    d.setName(TestEntityGenerator.newName());
    d.setStatus(TaxonomicStatus.AMBIGUOUS_SYNONYM);
    d.setExtinct(true);
    d.getLifezones().add(Lifezone.MARINE);
    d.getLifezones().add(Lifezone.BRACKISH);
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