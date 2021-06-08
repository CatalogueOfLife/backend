package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Citation;
import life.catalogue.api.model.CitationTest;
import life.catalogue.api.model.Dataset;

import life.catalogue.api.model.DatasetTest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.db.TestDataRule;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CitationMapperTest extends MapperTestBase<CitationMapper> {
  final int datasetKey = TestDataRule.APPLE.key;

  public CitationMapperTest() {
    super(CitationMapper.class);
  }

  private DatasetMapper dmapper(){
    return mapper(DatasetMapper.class);
  }

  @Test
  public void current() throws Exception {
    assertTrue(mapper().list(datasetKey).isEmpty());
    for (int id = 1; id<5; id++) {
      Citation c = CitationTest.create();
      c.setId("cit"+id);
      c.setYear("198"+id);
      mapper().create(datasetKey, c);
    }
    var list = mapper().list(datasetKey);
    commit();
    assertEquals(4, list.size());

    Dataset d = dmapper().get(datasetKey);
    assertEquals(list, d.getSource());

    mapper().delete(datasetKey);
    assertTrue(mapper().list(datasetKey).isEmpty());
  }

  @Test
  public void archive() throws Exception {
    for (int id = 1; id<5; id++) {
      Citation c = CitationTest.create();
      c.setId("cit"+id);
      c.setYear("198"+id);
      mapper().create(datasetKey, c);
    }

    final int attempt = 3;
    dmapper().updateLastImport(datasetKey, attempt);
    commit();

    mapper().createArchive(datasetKey);

    var list = mapper().listArchive(datasetKey, attempt);
    assertEquals(4, list.size());

    mapper().deleteArchive(datasetKey);
    assertTrue(mapper().listArchive(datasetKey, attempt).isEmpty());
  }

  @Test
  public void release() throws Exception {
    for (int id = 1; id<5; id++) {
      Citation c = CitationTest.create();
      c.setId("cit"+id);
      c.setYear("198"+id);
      mapper().create(datasetKey, c);
    }

    final int attempt = 3;
    dmapper().updateLastImport(datasetKey, attempt);
    mapper().createArchive(datasetKey);

    Dataset rel = DatasetTest.generateTestDataset();
    rel.setSourceKey(datasetKey);
    rel.setOrigin(DatasetOrigin.RELEASED);
    TestEntityGenerator.setUser(rel);
    dmapper().create(rel);
    commit();

    // add source to
    DatasetSourceMapper dcm = mapper(DatasetSourceMapper.class);
    Dataset src = dmapper().get(datasetKey);
    dcm.create(rel.getKey(), src);

    mapper().createRelease(datasetKey, rel.getKey(), attempt);
    var cites = mapper().listRelease(datasetKey, rel.getKey());
    assertEquals(4, cites.size());

    Dataset d = dcm.getReleaseSource(datasetKey, rel.getKey());
    assertEquals(cites, d.getSource());

  }
}