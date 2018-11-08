package org.col.db.mapper;

import org.col.api.model.IntKey;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

abstract class CRUDMapperTest<T extends IntKey, M extends CRUDMapper<T>> extends MapperTestBase<M> {
  
  public CRUDMapperTest(Class<M> mapperClazz) {
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
    assertEquals(u1, removeDbCreatedProps(u2));
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