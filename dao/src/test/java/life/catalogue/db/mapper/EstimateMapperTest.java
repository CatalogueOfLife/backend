package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Reference;
import life.catalogue.api.model.SpeciesEstimate;
import life.catalogue.api.search.EstimateSearchRequest;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.EstimateType;
import life.catalogue.junit.SqlSessionFactoryRule;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
  
  /** the batch form the archive export of a subtree uses, keyed on the taxon an estimate targets */
  @Test
  public void listByTaxa(){
    SpeciesEstimate e = createTestEntity(Datasets.COL);
    mapper().create(e);
    commit();

    final String targetId = e.getTarget().getId();
    assertEquals(1, mapper().listByTaxa(Datasets.COL, List.of(targetId)).size());
    assertTrue(mapper().listByTaxa(Datasets.COL, List.of("no-such-taxon")).isEmpty());
    assertEquals(1, mapper().listByTaxa(Datasets.COL, List.of("nope", targetId)).size());
  }

  @Test
  public void process(){
    // processing
    DecisionMapperTest.CountHandler handler = new DecisionMapperTest.CountHandler();
    mapper().processDataset(Datasets.COL).forEach(handler);
    assertEquals(0, handler.counter.size());
  }
}