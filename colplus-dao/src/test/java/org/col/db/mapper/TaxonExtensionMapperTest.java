package org.col.db.mapper;

import java.util.ArrayList;
import java.util.List;

import org.col.api.TestEntityGenerator;
import org.col.api.model.IntKey;
import org.col.api.model.Taxon;
import org.col.api.model.UserManaged;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

abstract class TaxonExtensionMapperTest<T extends IntKey & UserManaged, M extends TaxonExtensionMapper<T>> extends MapperTestBase<M> {
  
  public TaxonExtensionMapperTest(Class<M> mapperClazz) {
    super(mapperClazz);
  }
  
  abstract List<T> createTestEntities();
  
  @Test
  public void roundtrip() throws Exception {
    final int datasetKey = TestEntityGenerator.DATASET11.getKey();
    
    Taxon t = TestEntityGenerator.newTaxon();
    t.setDatasetKey(datasetKey);
    mapper(TaxonMapper.class).create(t);
    commit();
    final String taxonID = t.getId();
    
    List<T> originals = new ArrayList<>();
    for (T obj : createTestEntities()) {
      mapper().create(obj, taxonID, datasetKey);
      originals.add(TestEntityGenerator.nullifyDate(obj));
    }
    commit();

    T obj = TestEntityGenerator.nullifyDate(mapper().get(datasetKey, originals.get(0).getKey()));
    assertEquals(obj, originals.get(0));
  
    List<T> created = TestEntityGenerator.nullifyDate(mapper().listByTaxon(datasetKey, taxonID));
    assertEquals(originals, created);
  }
  
}