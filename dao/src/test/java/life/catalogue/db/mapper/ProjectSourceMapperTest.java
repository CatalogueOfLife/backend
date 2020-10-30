package life.catalogue.db.mapper;

import life.catalogue.api.model.ArchivedDataset;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetMetadata;
import life.catalogue.api.vocab.Datasets;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class ProjectSourceMapperTest extends MapperTestBase<ProjectSourceMapper> {

  public ProjectSourceMapperTest() {
    super(ProjectSourceMapper.class);
  }

  public static ArchivedDataset createProjectSource() {
    ArchivedDataset d = new ArchivedDataset();
    DatasetMapperTest.populate(d);
    d.setSourceKey(Datasets.COL);
    d.setImportAttempt(3);
    return d;
  }

  @Test
  public void deleteByProject() throws Exception {
    mapper().deleteByProject(Datasets.COL);
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
    ArchivedDataset d1 = new Dataset(createProjectSource());
    Dataset d = new Dataset(d1);
    mapper(DatasetMapper.class).create(d);
    d1.setKey(d.getKey());

    ArchivedDataset d2 = mapper().getProjectSource(d1.getKey(), Datasets.COL);
    DatasetMetadata m1 = DatasetMetadata.copy(d1);
    DatasetMetadata m2 = DatasetMetadata.copy(d2);
    assertEquals(m2, m1);
    commit();
  }

  @Test
  public void roundtripRelease() throws Exception {
    ArchivedDataset d1 = createProjectSource();
    d1.setKey(100);
    mapper().create(Datasets.COL, d1);

    ArchivedDataset d2 = mapper().getReleaseSource(d1.getKey(), Datasets.COL);
    assertEquals(d2, d1);
    commit();
  }

}
