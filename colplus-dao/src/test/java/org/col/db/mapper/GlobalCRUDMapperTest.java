package org.col.db.mapper;

import org.col.api.model.GlobalEntity;
import org.col.db.GlobalCRUD;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

abstract class GlobalCRUDMapperTest<T extends GlobalEntity, M extends GlobalCRUD<T>> extends MapperTestBase<M> {
  
  public GlobalCRUDMapperTest(Class<M> mapperClazz) {
    super(mapperClazz);
  }
  
  abstract T createTestEntity();
  
  abstract T removeDbCreatedProps(T obj);
  
  @Test
  public void roundtrip() throws Exception {
    T u1 = createTestEntity();
    mapper().create(u1);
    commit();
    
    T u2 = mapper().get(u1.getKey());
    removeDbCreatedProps(u2);
    printDiff(u1, u2);
    assertEquals(u1, u2);
  }
  
  abstract void updateTestObj(T obj);
  
  @Test
  public void update() throws Exception {
    T u1 = createTestEntity();
    mapper().create(u1);
    commit();
    
    updateTestObj(u1);
    mapper().update(u1);
    commit();
    
    T u2 = mapper().get(u1.getKey());
    //printDiff(u1, u2);
    assertEquals(u1, removeDbCreatedProps(u2));
  }
  
  @Test
  public void deleted() throws Exception {
    T u1 = createTestEntity();
    mapper().create(u1);
    commit();
    
    mapper().delete(u1.getKey());
    commit();
    
    assertNull(mapper().get(u1.getKey()));
  }
  
}