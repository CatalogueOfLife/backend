package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.Datasets;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class DatasetSourceMapperTest extends MapperTestBase<DatasetSourceMapper> {

  public DatasetSourceMapperTest() {
    super(DatasetSourceMapper.class);
  }

  public static Dataset createProjectSource() {
    Dataset d = new Dataset();
    DatasetMapperTest.populate(d);
    d.setSourceKey(Datasets.COL);
    d.setAttempt(3);
    d.setGbifPublisherKey(null);
    d.setGbifKey(null);
    d.setSize(null);
    return d;
  }

  @Test
  public void deleteByProject() throws Exception {
    mapper().deleteByRelease(Datasets.COL);
  }

  @Test
  public void listProjectSources() throws Exception {
    mapper().listProjectSources(Datasets.COL);
  }

  @Test
  public void listReleaseSources() throws Exception {
    mapper().listReleaseSources(Datasets.COL);
  }

  @Test
  public void roundtripProject() throws Exception {
    Dataset ds = new Dataset(createProjectSource());
    Dataset d = new Dataset(ds);
    mapper(DatasetMapper.class).create(d);
    ds.setKey(d.getKey());

    Dataset ds2 = mapper().getProjectSource(ds.getKey(), Datasets.COL);
    // no import attempt expected as there are no synced sectors
    ds.setAttempt(null);

    commit();
    assertEquals(ds2, ds);
  }

  @Test
  public void roundtripRelease() throws Exception {
    Dataset d1 = createProjectSource();
    d1.setKey(TestEntityGenerator.DATASET11.getKey());
    mapper().create(Datasets.COL, d1);

    Dataset d2 = removeDbCreatedProps(mapper().getReleaseSource(d1.getKey(), Datasets.COL));
    assertEquals(d2, d1);
    commit();
  }

  Dataset removeDbCreatedProps(Dataset obj) {
    obj.setCreated(null);
    obj.setModified(null);
    return obj;
  }
}
