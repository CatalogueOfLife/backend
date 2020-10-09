package life.catalogue.db.mapper;

import life.catalogue.api.model.ArchivedDataset;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.Datasets;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 *
 */
public class ProjectSourceMapperTest extends MapperTestBase<ProjectSourceMapper> {

  public ProjectSourceMapperTest() {
    super(ProjectSourceMapper.class);
  }

  public static ProjectSourceDataset createProjectSource() {
    ProjectSourceDataset d = new ProjectSourceDataset();
    DatasetMapperTest.populate(d);
    return d;
  }

  private DatasetMapper dmapper(){
    return mapper(DatasetMapper.class);
  }


  @Test
  public void deleteByDataset() throws Exception {
    mapper().deleteByDataset(Datasets.COL);
  }

  @Test
  public void projectArchive() throws Exception {
    ProjectSourceDataset d1 = createProjectSource();
    d1.setDatasetKey(Datasets.COL);
    d1.setImportAttempt(3);
    createDataset(d1);

    mapper().create(d1);
    ArchivedDataset d2 = mapper().get(d1.getKey(), Datasets.COL);
    assertTrue(d2.equals(d1));
    commit();
  }


  /**
   * @return created dataset key
   */
  private int createDataset(ProjectSourceDataset psd){
    Dataset d = new Dataset(psd);
    d.setSourceKey(psd.getSourceKey());
    dmapper().create(d);
    psd.setKey(d.getKey());
    return d.getKey();
  }

}
