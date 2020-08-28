package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DatasetScopedEntity;
import life.catalogue.dao.Partitioner;
import life.catalogue.db.PgSetupRule;
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
    Partitioner.partition(PgSetupRule.getSqlSessionFactory(), 999);
    Partitioner.createManagedObjects(PgSetupRule.getSqlSessionFactory(), 999);
    mapper().copyDataset(datasetKey, 999);
  }

  T createTestEntityIncId(int datasetKey) {
    T ent = createTestEntity(datasetKey);
    ent.setDatasetKey(datasetKey);
    ent.setId(TestEntityGenerator.ID_GEN.incrementAndGet());
    return ent;
  }
  
}
