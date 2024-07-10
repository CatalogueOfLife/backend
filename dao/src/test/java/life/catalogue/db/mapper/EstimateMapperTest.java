package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Reference;
import life.catalogue.api.model.SpeciesEstimate;
import life.catalogue.api.search.EstimateSearchRequest;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.EstimateType;
import life.catalogue.junit.SqlSessionFactoryRule;

import org.apache.ibatis.session.SqlSession;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EstimateMapperTest extends BaseDecisionMapperTest<SpeciesEstimate, EstimateSearchRequest, EstimateMapper> {
  Reference ref;
  
  public EstimateMapperTest() {
    super(EstimateMapper.class);
  }
  
  @Before
  public void init() {
    ref = TestEntityGenerator.newReference("Bam bam");
    ref.setDatasetKey(Datasets.COL);
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      session.getMapper(ReferenceMapper.class).create(ref);
    }
  }
  
  @Override
  SpeciesEstimate createTestEntity(int dkey) {
    SpeciesEstimate d = new SpeciesEstimate();
    d.setDatasetKey(Datasets.COL);
    d.setTarget(TestEntityGenerator.newSimpleNameWithoutStatusParent());
    d.setEstimate(34567);
    d.setType(EstimateType.SPECIES_EXTINCT);
    d.setReferenceId(ref.getId());
    d.setRemarks("I cannot remember why I did this.");
    d.setCreatedBy(TestEntityGenerator.USER_EDITOR.getKey());
    d.setModifiedBy(d.getCreatedBy());
    return d;
  }
  
  @Override
  SpeciesEstimate removeDbCreatedProps(SpeciesEstimate obj) {
    obj.setCreated(null);
    obj.setModified(null);
    obj.getTarget().setBroken(false);
    return obj;
  }
  
  @Override
  void updateTestObj(SpeciesEstimate obj) {
    obj.setRemarks("My next note");
    obj.setEstimate(1289);
  }
  
  @Test
  public void process(){
    // processing
    DecisionMapperTest.CountHandler handler = new DecisionMapperTest.CountHandler();
    mapper().processDataset(Datasets.COL).forEach(handler);
    assertEquals(0, handler.counter.size());
  }
}