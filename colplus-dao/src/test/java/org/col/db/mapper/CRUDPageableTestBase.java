package org.col.db.mapper;

import java.util.ArrayList;
import java.util.List;

import org.col.api.TestEntityGenerator;
import org.col.api.model.DSID;
import org.col.api.model.Dataset;
import org.col.api.model.DatasetScopedEntity;
import org.col.api.model.Page;
import org.col.api.vocab.DatasetType;
import org.col.api.vocab.Users;
import org.col.dao.Partitioner;
import org.col.db.CRUD;
import org.col.db.DatasetPageable;
import org.col.db.PgSetupRule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 *
 */
abstract class CRUDPageableTestBase<T extends DatasetScopedEntity<String>, M extends CRUD<DSID<String>, T> & DatasetPageable<T>>
    extends CRUDTestBase<DSID<String>, T, M> {
  
  public CRUDPageableTestBase(Class<M> mapperClazz) {
    super(mapperClazz);
  }
  
  public int newDataset(){
    Dataset d = new Dataset();
    d.setTitle("New dataset");
    d.setType(DatasetType.GLOBAL);
    d.applyUser(Users.TESTER);
    mapper(DatasetMapper.class).create(d);
    return d.getKey();
  }
  
  @Test
  public void getEmpty() throws Exception {
    assertNull(mapper().get(DSID.key(datasetKey, "")));
  }
  
  @Test
  public void countAndList() throws Exception {
    final int dkey = newDataset();
    
    Partitioner.partition(PgSetupRule.getSqlSessionFactory(), dkey);
  
    List<T> in = new ArrayList<>();
    in.add(createTestEntityIncId(dkey));
    in.add(createTestEntityIncId(dkey));
    in.add(createTestEntityIncId(dkey));
    in.add(createTestEntityIncId(dkey));
    in.add(createTestEntityIncId(dkey));
    for (T obj : in) {
      mapper().create(obj);
    }
    generateDatasetImport(dkey);
    commit();
  
    assertEquals(in.size(), mapper().count(dkey));
    
    List<T> out = mapper().list(dkey, new Page(0, 10));
    assertEquals(5, out.size());
  
  
    removeDbCreatedProps(in);
    removeDbCreatedProps(out);
    assertEquals(in.get(0), out.get(0));
    assertEquals(in.get(1), out.get(1));
    assertEquals(in.get(2), out.get(2));
    assertEquals(in.get(3), out.get(3));
    assertEquals(in.get(4), out.get(4));
    assertEquals(in, out);
  
  
    out = mapper().list(dkey, new Page(2,2));
    assertEquals(2, out.size());
  
    removeDbCreatedProps(in);
    removeDbCreatedProps(out);
    assertEquals(in.get(2), out.get(0));
    assertEquals(in.get(3), out.get(1));
  }
  
  private T createTestEntityIncId(int datasetKey) {
    T ent = createTestEntity(datasetKey);
    ent.setId(String.valueOf(TestEntityGenerator.ID_GEN.incrementAndGet()));
    return ent;
  }
  
}
