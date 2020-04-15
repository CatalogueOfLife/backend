package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.DatasetScopedEntity;
import life.catalogue.db.CRUD;
import life.catalogue.db.DatasetPageable;
import org.junit.Test;

import static org.junit.Assert.assertNull;

/**
 *
 */
abstract class BaseDecisionMapperTest<T extends DatasetScopedEntity<Integer>, R, M extends BaseDecisionMapper<T, R>>
    extends CRUDPageableTestBase<Integer, T, M> {

  public BaseDecisionMapperTest(Class<M> mapperClazz) {
    super(mapperClazz);
  }

  T createTestEntityIncId(int datasetKey) {
    T ent = createTestEntity(datasetKey);
    ent.setDatasetKey(datasetKey);
    ent.setId(TestEntityGenerator.ID_GEN.incrementAndGet());
    return ent;
  }
  
}
