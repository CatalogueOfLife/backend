package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetScopedEntity;
import life.catalogue.api.model.Page;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.Users;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.Partitioner;
import life.catalogue.db.CRUD;
import life.catalogue.db.DatasetPageable;
import life.catalogue.db.DatasetProcessable;
import life.catalogue.db.PgSetupRule;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 */
abstract class CRUDPageableTestBase<K, T extends DatasetScopedEntity<K>, M extends CRUD<DSID<K>, T> & DatasetPageable<T> & DatasetProcessable<T>>
    extends CRUDTestBase<DSID<K>, T, M> {

  final DatasetDao.KeyGenerator gen = new DatasetDao.KeyGenerator(10, 12, 10);

  public CRUDPageableTestBase(Class<M> mapperClazz) {
    super(mapperClazz);
  }
  
  public int newDataset(){
    Dataset d = new Dataset();
    d.setTitle("New dataset");
    d.setType(DatasetType.TAXONOMIC);
    d.setOrigin(DatasetOrigin.MANAGED);
    d.applyUser(Users.TESTER);
    gen.setKey(d);
    mapper(DatasetMapper.class).create(d);
    Partitioner.partition(PgSetupRule.getSqlSessionFactory(), d.getKey(), d.getOrigin());
    return d.getKey();
  }

  @Test
  public void deleteByDataset() throws Exception {
    mapper().deleteByDataset(datasetKey);
  }
  
  @Test
  public void countAndList() throws Exception {
    final int dkey = newDataset();
    
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
  
  abstract T createTestEntityIncId(int datasetKey);

}
