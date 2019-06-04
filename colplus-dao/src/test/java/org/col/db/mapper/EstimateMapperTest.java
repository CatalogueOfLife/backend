package org.col.db.mapper;

import org.col.api.TestEntityGenerator;
import org.col.api.model.SpeciesEstimate;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.newNameRef;

public class EstimateMapperTest extends GlobalCRUDMapperTest<SpeciesEstimate, EstimateMapper> {
  
  public EstimateMapperTest() {
    super(EstimateMapper.class);
  }
  
  @Test
  public void getById() {
  }
  
  @Test
  public void broken() {
  }
  
  @Override
  SpeciesEstimate createTestEntity() {
    SpeciesEstimate d = new SpeciesEstimate();
    d.setSubject(newNameRef());
    d.setEstimate(34567);
    d.setReferenceId("ftvbhjnjklm,");
    d.setNote("I cannot remember why I did this.");
    d.setCreatedBy(TestEntityGenerator.USER_EDITOR.getKey());
    d.setModifiedBy(d.getCreatedBy());
    return d;
  }
  
  @Override
  SpeciesEstimate removeDbCreatedProps(SpeciesEstimate obj) {
    obj.setCreated(null);
    obj.setModified(null);
    return obj;
  }
  
  @Override
  void updateTestObj(SpeciesEstimate obj) {
    obj.setNote("My next note");
    obj.setEstimate(1289);
  }
  
}