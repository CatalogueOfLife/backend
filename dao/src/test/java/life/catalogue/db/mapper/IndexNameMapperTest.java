package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.IndexName;

/**
 *
 */
public class IndexNameMapperTest extends CRUDTestBase<Integer, IndexName, NamesIndexMapper> {

  public IndexNameMapperTest() {
    super(NamesIndexMapper.class);
  }
  
  @Override
  IndexName createTestEntity(int dkey) {
    IndexName n = new IndexName(TestEntityGenerator.newName(dkey));
    n.setKey(null);
    return n;
  }

  @Override
  void updateTestObj(IndexName obj) {
    obj.setAuthorship("Berta & Tomate");
  }

  @Override
  IndexName removeDbCreatedProps(IndexName n) {
    n.setModifiedBy(null);
    n.setCreatedBy(null);
    return n;
  }

}
