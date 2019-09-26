package org.col.db.mapper;

import org.apache.ibatis.session.SqlSession;
import org.col.api.TestEntityGenerator;
import org.col.api.model.Reference;
import org.col.api.model.SpeciesEstimate;
import org.col.api.vocab.Datasets;
import org.col.api.vocab.EstimateType;
import org.col.db.PgSetupRule;
import org.junit.Before;

public class EstimateMapperTest extends GlobalCRUDMapperTest<SpeciesEstimate, EstimateMapper> {
  Reference ref;
  
  public EstimateMapperTest() {
    super(EstimateMapper.class);
  }
  
  @Before
  public void init() {
    ref = TestEntityGenerator.newReference("Bam bam");
    ref.setDatasetKey(Datasets.DRAFT_COL);
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      session.getMapper(ReferenceMapper.class).create(ref);
    }
  }
  
  @Override
  SpeciesEstimate createTestEntity() {
    SpeciesEstimate d = new SpeciesEstimate();
    d.setDatasetKey(Datasets.DRAFT_COL);
    d.setSubject(TestEntityGenerator.newSimpleNameWithoutStatusParent());
    d.setEstimate(34567);
    d.setType(EstimateType.DESCRIBED_SPECIES_EXTINCT);
    d.setReferenceId(ref.getId());
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