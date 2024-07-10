package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.junit.TestDataRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

abstract class TaxonExtensionMapperTest<T extends ExtensionEntity, M extends TaxonExtensionMapper<T>> extends MapperTestBase<M> {
  
  Taxon tax;
  List<T> originals;

  public TaxonExtensionMapperTest(Class<M> mapperClazz) {
      super(mapperClazz, TestDataRule.empty());
  }

  /**
   * @return at least 3 entities, more allowed !
   */
  abstract List<T> createTestEntities();

  @Test
  public void copyDataset() throws Exception {
    CopyDatasetTestComponent.copy(mapper(), Datasets.COL, true);
  }

  @Test
  public void sectorProcessable() throws Exception {
    SectorProcessableTestComponent.test(mapper(), DSID.of(Datasets.COL, 1));
  }

  /**
   * @return list of created entities
   */
  @Test
  public void roundtrip() throws Exception {
    // prepare taxon to hook extensions to
    tax = TestEntityGenerator.newTaxon(3);
    insertTaxon(tax);
    
    final List<T> entities = createTestEntities();
    assertTrue("At least 3 test entities are needed", entities.size() > 2);
    originals = new ArrayList<>();
    for (T obj : entities) {
      obj.setDatasetKey(tax.getDatasetKey());
      // test create
      mapper().create(obj, tax.getId());
      originals.add(TestEntityGenerator.nullifyDate(obj));
    }
    commit();

    // test get
    T obj = TestEntityGenerator.nullifyDate(mapper().get(originals.get(0)));
    assertEquals(originals.get(0), obj);
    
    // test listByTaxon
    List<T> created = TestEntityGenerator.nullifyDate(mapper().listByTaxon(tax));
    assertEquals(originals, created);
  
    // processing
    CountHandler handler = new CountHandler();
    mapper().processDataset(tax.getDatasetKey()).forEach(handler);
    assertEquals(1, handler.counter.size());
    assertEquals(originals.size(), (int) handler.counter.get(tax.getId()));
  }
  
  public class CountHandler implements Consumer<TaxonExtension<T>> {
    Map<String, Integer> counter = new HashMap<>();
    
    public void accept(TaxonExtension<T> te) {
      assertEquals(tax.getId(), te.getTaxonID());
      if (counter.containsKey(te.getTaxonID())) {
        counter.put(te.getTaxonID(), counter.get(te.getTaxonID()) + 1);
      } else {
        counter.put(te.getTaxonID(), 1);
      }
    }
  }
}