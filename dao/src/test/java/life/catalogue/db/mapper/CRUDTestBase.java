package life.catalogue.db.mapper;

import life.catalogue.api.model.Entity;
import life.catalogue.db.CRUD;
import life.catalogue.db.TestDataRule;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

abstract class CRUDTestBase<K, V extends Entity<K>, M extends CRUD<K, V>> extends MapperTestBase<M> {

  CRUDTestBase(Class<M> mapperClazz) {
    super(mapperClazz);
  }

  CRUDTestBase(Class<M> mapperClazz, TestDataRule.TestData testData) {
    super(mapperClazz, new TestDataRule(testData));
  }
  
  abstract V createTestEntity();
  
  V removeDbCreatedProps(V obj) {
    // nothing by default
    return obj;
  }

  List<V> removeDbCreatedProps(List<V> objs) {
    objs.forEach(this::removeDbCreatedProps);
    return objs;
  }

  void removeDbCreatedProps(V... objs) {
    if (objs != null) {
      for (V obj : objs) {
        removeDbCreatedProps(obj);
      }
    }
  }

  @Test
  public void roundtrip() throws Exception {
    V u1 = createTestEntity();
    mapper().create(u1);
    commit();
    
    V u2 = removeDbCreatedProps(mapper().get(u1.getKey()));
    removeDbCreatedProps(u1);
    //printDiff(u1, u2);
    assertEquals(u1, u2);
  }
  
  abstract void updateTestObj(V obj);
  
  @Test
  public void update() throws Exception {
    V u1 = createTestEntity();
    mapper().create(u1);
    commit();
    
    updateTestObj(u1);
    mapper().update(u1);
    commit();
  
    V u2 = removeDbCreatedProps(mapper().get(u1.getKey()));
    removeDbCreatedProps(u1);

    //printDiff(u1, u2);
    assertEquals(u1, u2);
  }
  
  @Test
  public void deleted() throws Exception {
    V u1 = createTestEntity();
    mapper().create(u1);
    commit();
    
    mapper().delete(u1.getKey());
    commit();
    
    assertNull(mapper().get(u1.getKey()));
  }
  
}