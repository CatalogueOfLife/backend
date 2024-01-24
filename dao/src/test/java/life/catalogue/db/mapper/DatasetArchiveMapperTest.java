package life.catalogue.db.mapper;

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
    d1.setSize(null); // we populate size by counting usages - ignore it in comparison

    Dataset d2 = mapper().get(d1.getKey(), d1.getAttempt());

    assertTrue(d2.equals(d1));
  }

  @Test
  public void deleteByDataset() throws Exception {
    mapper().deleteByDataset(Datasets.COL);
  }

}
