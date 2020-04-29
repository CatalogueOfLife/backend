package life.catalogue.db.mapper;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.Datasets;
import org.junit.Test;

import static life.catalogue.db.mapper.DatasetMapperTest.create;
import static life.catalogue.db.mapper.DatasetMapperTest.rmDbCreatedProps;
import static org.junit.Assert.assertEquals;
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
    mapper().createArchive(d1.getKey());
    commit();
    // reload to also get the creation/modified dates,
    d1 = dmapper().get(d1.getKey());
    // but remove the editors as we dont keep them in archives
    d1.getEditors().clear();
    d1.getSettings().clear();

    Dataset d2 = mapper().getArchive(d1.getKey(), d1.getImportAttempt());
    
    printDiff(d1, d2);
    assertEquals(d1, d2);
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
    dmapper().create(d1);

    mapper().createProjectArchive(d1);
    Dataset d2 = mapper().getProjectArchive(d1.getKey(), d1.getImportAttempt(), Datasets.DRAFT_COL);

    rmDbCreatedProps(d1);
    d1.getEditors().clear();
    d1.getSettings().clear();
    rmDbCreatedProps(d2);
    assertTrue(d2.equals(d1));
  }

}
