package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DataEntity;
import life.catalogue.db.CRUD;
import life.catalogue.junit.TestDataRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

abstract class CRUDEntityTestBase<K, V extends DataEntity<K>, M extends CRUD<K, V>> extends CRUDTestBase<K,V,M> {
  
  protected static int datasetKey = TestEntityGenerator.DATASET11.getKey();;


  CRUDEntityTestBase(Class<M> mapperClazz) {
    super(mapperClazz);
  }

  CRUDEntityTestBase(Class<M> mapperClazz, TestDataRule.TestData testData) {
    super(mapperClazz, testData);
  }
  
  abstract V createTestEntity(int datasetKey);

  @Override
  final V createTestEntity() {
    return createTestEntity(datasetKey);
  }
  
}