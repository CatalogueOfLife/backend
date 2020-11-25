package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Issue;
import org.junit.Test;

import static org.junit.Assert.*;

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
  public void delete() {
    int datasetKey = TestEntityGenerator.TAXON1.getDatasetKey();
    Taxon t = new Taxon(TestEntityGenerator.TAXON1);

    VerbatimSource v1 = new VerbatimSource();
    v1.setKey(t);
    v1.setSourceId("source77");
    v1.setSourceDatasetKey(77);
    mapper().create(v1);

    commit();
    assertNotNull(mapper().get(v1));

    // non existing sector
    mapper().deleteBySector(DSID.of(datasetKey, 1));
    assertNotNull(mapper().get(v1));

    Sector s = new Sector();
    s.setDatasetKey(datasetKey);
    s.setSubjectDatasetKey(datasetKey);
    s.setTarget(SimpleNameLink.of(t));
    s.setMode(Sector.Mode.ATTACH);
    TestEntityGenerator.setUser(s);
    mapper(SectorMapper.class).create(s);

    t.setSectorKey(s.getId());
    mapper(TaxonMapper.class).update(t);
    assertNotNull(mapper().get(v1));

    mapper().deleteBySector(s);
    assertNull(mapper().get(v1));
  }

  @Test
  public void copyDataset() throws Exception {
    CopyDatasetTestComponent.copy(mapper(), datasetKey, true);
  }
}