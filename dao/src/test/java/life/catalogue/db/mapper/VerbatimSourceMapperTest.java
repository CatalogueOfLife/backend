package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.VerbatimSource;
import life.catalogue.api.vocab.Issue;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VerbatimSourceMapperTest extends MapperTestBase<VerbatimSourceMapper> {

  int datasetKey = testDataRule.testData.key;

  public VerbatimSourceMapperTest() {
    super(VerbatimSourceMapper.class);
  }

  @Test
  public void roundtrip() {
    VerbatimSource v1 = new VerbatimSource();
    v1.setKey(TestEntityGenerator.TAXON1);
    v1.setSourceId("source77");
    v1.setSourceDatasetKey(77);
    v1.addIssues(Issue.AUTHORSHIP_CONTAINS_TAXONOMIC_NOTE, Issue.BASIONYM_DERIVED);
    mapper().create(v1);

    commit();

    VerbatimSource v2 = mapper().get(v1);

    assertEquals(v1, v2);
  }

  @Test
  public void copyDataset() throws Exception {
    CopyDatasetTestComponent.copy(mapper(), datasetKey, true);
  }
}