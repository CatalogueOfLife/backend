package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SpeciesInteraction;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.SpeciesInteractionType;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static life.catalogue.api.TestEntityGenerator.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SpeciesInteractionMapperTest extends MapperTestBase<SpeciesInteractionMapper> {

  private SpeciesInteractionMapper mapper;

  public SpeciesInteractionMapperTest() {
    super(SpeciesInteractionMapper.class);
  }

  @Before
  public void init() {
    mapper = testDataRule.getMapper(SpeciesInteractionMapper.class);
  }

  @Test
  public void roundtrip() throws Exception {
    SpeciesInteraction in = nullifyDate(newRelation());
    mapper.create(in);
    assertNotNull(in.getKey());
    commit();
    List<SpeciesInteraction> outs = mapper.listByTaxon(in.getTaxonKey());
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

  private static SpeciesInteraction newRelation(SpeciesInteractionType type) {
    SpeciesInteraction na = TestEntityGenerator.setUserDate(new SpeciesInteraction());
    na.setDatasetKey(DATASET11.getKey());
    na.setType(type);
    na.setTaxonId(TAXON1.getId());
    na.setRelatedTaxonId(TAXON2.getId());
    return na;
  }

  private static SpeciesInteraction newRelation() {
    return newRelation(SpeciesInteractionType.EATS);
  }

}