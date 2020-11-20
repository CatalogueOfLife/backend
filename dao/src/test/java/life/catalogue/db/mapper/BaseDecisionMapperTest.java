package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DatasetScopedEntity;
import org.junit.Test;

/**
 *
 */
abstract class BaseDecisionMapperTest<T extends DatasetScopedEntity<Integer>, R, M extends BaseDecisionMapper<T, R>>
    extends CRUDPageableTestBase<Integer, T, M> {

  public BaseDecisionMapperTest(Class<M> mapperClazz) {
    super(mapperClazz);
  }

  @Test
  public void copyDataset() throws Exception {
    CopyDatasetTestComponent.copy(mapper(), testDataRule.testData.key, true);
  }

  T createTestEntityIncId(int datasetKey) {
    T ent = createTestEntity(datasetKey);
    ent.setDatasetKey(datasetKey);
    ent.setId(TestEntityGenerator.ID_GEN.incrementAndGet());
    return ent;
  }
  
}
