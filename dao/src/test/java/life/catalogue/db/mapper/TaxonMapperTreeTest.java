package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.DSIDValue;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.model.TaxonSectorCountMap;
import life.catalogue.db.TestDataRule;

import java.util.List;

import org.junit.Test;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import static life.catalogue.api.TestEntityGenerator.DATASET11;
import static life.catalogue.api.TestEntityGenerator.NAME4;
import static org.junit.Assert.*;


public class TaxonMapperTreeTest extends MapperTestBase<TaxonMapper> {
  
  public TaxonMapperTreeTest() {
    super(TaxonMapper.class, TestDataRule.tree());
  }
  
  
  @Test
  public void classificationCounts() throws Exception {
    DSIDValue<String> key = DSID.of(DATASET11.getKey(), "t20");
    List<TaxonSectorCountMap> x = mapper().classificationCounts(key);
    assertEquals(6, x.size());
    for (TaxonSectorCountMap c : x) {
      assertNotNull(c.getId());
      assertNotNull(c.getCount());
      assertTrue(c.getCount().isEmpty());
    }

    mapper().updateDatasetSectorCount(key.id("t2"), null);
    mapper().updateDatasetSectorCount(key.id("t2"), new Int2IntOpenHashMap());
    Int2IntOpenHashMap cnt = new Int2IntOpenHashMap();
    cnt.put(45, 6);
    cnt.put(4, 666);
    cnt.put(13, 169);
    mapper().updateDatasetSectorCount(key.id("t3"), cnt);
    x = mapper().classificationCounts(key.id("t20"));
    assertEquals(6, x.size());
    for (TaxonSectorCountMap c : x) {
      assertNotNull(c.getId());
      assertNotNull(c.getCount());
    }
  }
  
  @Test
  public void classificationSimple() throws Exception {
    List<?> cl = mapper().classificationSimple(DSID.of(NAME4.getDatasetKey(), "t15"));
    assertEquals(7, cl.size());
  }
  
}
