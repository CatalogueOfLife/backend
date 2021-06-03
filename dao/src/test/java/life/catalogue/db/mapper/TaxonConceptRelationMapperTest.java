package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.TaxonConceptRelation;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.TaxonConceptRelType;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import static life.catalogue.api.TestEntityGenerator.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TaxonConceptRelationMapperTest  extends MapperTestBase<TaxonConceptRelationMapper> {

  private TaxonConceptRelationMapper mapper;

  public TaxonConceptRelationMapperTest() {
    super(TaxonConceptRelationMapper.class);
  }

  @Before
  public void init() {
    mapper = testDataRule.getMapper(TaxonConceptRelationMapper.class);
  }

  @Test
  public void roundtrip() throws Exception {
    TaxonConceptRelation in = nullifyDate(newRelation());
    mapper.create(in);
    assertNotNull(in.getKey());
    commit();
    List<TaxonConceptRelation> outs = mapper.listByTaxon(in.getTaxonKey());
    assertEquals(1, outs.size());
    assertEquals(in, nullifyDate(outs.get(0)));
  }

  @Test
  public void sectorProcessable() throws Exception {
    SectorProcessableTestComponent.test(mapper(), DSID.of(Datasets.COL, 1));
  }

  @Test
  public void copyDataset() throws Exception {
    CopyDatasetTestComponent.copy(mapper, Datasets.COL, true);
  }

  private static TaxonConceptRelation newRelation(TaxonConceptRelType type) {
    TaxonConceptRelation na = TestEntityGenerator.setUserDate(new TaxonConceptRelation());
    na.setDatasetKey(DATASET11.getKey());
    na.setType(type);
    na.setTaxonId(TAXON1.getId());
    na.setRelatedTaxonId(TAXON2.getId());
    return na;
  }

  private static TaxonConceptRelation newRelation() {
    return newRelation(TaxonConceptRelType.EQUALS);
  }

}