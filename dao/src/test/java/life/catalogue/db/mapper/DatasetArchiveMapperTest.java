package life.catalogue.db.mapper;

import life.catalogue.api.model.ArchivedDataset;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.Datasets;
import org.junit.Test;

import static life.catalogue.db.mapper.DatasetMapperTest.create;
import static org.junit.Assert.assertTrue;
/**
 *
 */
public class DatasetArchiveMapperTest extends MapperTestBase<DatasetArchiveMapper> {

  public DatasetArchiveMapperTest() {
    super(DatasetArchiveMapper.class);
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
  public void archive() throws Exception {
    Dataset d1 = create();
    dmapper().create(d1);
    dmapper().updateLastImport(d1.getKey(), 3);

    mapper().create(d1.getKey());
    commit();
    // reload to also get the creation/modified dates,
    d1 = dmapper().get(d1.getKey());

    ArchivedDataset d2 = mapper().get(d1.getKey(), d1.getImportAttempt());
    
    assertTrue(d2.equals(d1));
  }

  @Test
  public void deleteByDataset() throws Exception {
    mapper().deleteByDataset(Datasets.DRAFT_COL);
  }

  @Test
  public void projectArchive() throws Exception {
    ProjectSourceDataset d1 = createProjectSource();
    d1.setDatasetKey(Datasets.DRAFT_COL);
    d1.setImportAttempt(3);
    createDataset(d1);

    mapper().createProjectSource(d1);
    ArchivedDataset d2 = mapper().getProjectSource(d1.getKey(), d1.getImportAttempt(), Datasets.DRAFT_COL);

    assertTrue(d2.equals(d1));
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
