package org.col.db.mapper;

import java.util.List;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.col.api.model.Taxon;
import org.col.api.model.TaxonCountMap;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.DATASET11;
import static org.col.api.TestEntityGenerator.NAME4;
import static org.junit.Assert.*;


public class TaxonMapperTreeTest extends MapperTestBase<TaxonMapper> {
  
  NameUsageMapperTreeTest.CountHandler<Taxon> countHandler;
  
  public TaxonMapperTreeTest() {
    super(TaxonMapper.class, TestDataRule.tree());
  }
  
  
  @Test
  public void classificationCounts() throws Exception {
    List<TaxonCountMap> x = mapper().classificationCounts(DATASET11.getKey(), "t20");
    assertEquals(6, x.size());
    for (TaxonCountMap c : x) {
      assertNotNull(c.getId());
      assertNotNull(c.getCount());
      assertTrue(c.getCount().isEmpty());
    }

    mapper().updateDatasetSectorCount(DATASET11.getKey(), "t2", null);
    mapper().updateDatasetSectorCount(DATASET11.getKey(), "t2", new Int2IntOpenHashMap());
    Int2IntOpenHashMap cnt = new Int2IntOpenHashMap();
    cnt.put(45, 6);
    cnt.put(4, 666);
    cnt.put(13, 169);
    mapper().updateDatasetSectorCount(DATASET11.getKey(), "t3", cnt);
    
    x = mapper().classificationCounts(DATASET11.getKey(), "t20");
    assertEquals(6, x.size());
    for (TaxonCountMap c : x) {
      assertNotNull(c.getId());
      assertNotNull(c.getCount());
    }
  }
  
  @Test
  public void classificationSimple() throws Exception {
    List<?> cl = mapper().classificationSimple(NAME4.getDatasetKey(), "t15");
    assertEquals(7, cl.size());
  }
  
}
