package life.catalogue.db.mapper;

import life.catalogue.api.model.ArchivedDataset;
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
  public void roundtrip() throws Exception {
    ArchivedDataset d1 = createProjectSource();
    d1.setKey(100);
    mapper().create(Datasets.COL, d1);

    ArchivedDataset d2 = mapper().get(d1.getKey(), Datasets.COL);
    assertEquals(d2, d1);
    commit();
  }

}
