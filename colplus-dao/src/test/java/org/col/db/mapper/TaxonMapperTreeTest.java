package org.col.db.mapper;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.col.api.model.Taxon;
import org.col.api.model.TaxonCountMap;
import org.junit.Assert;
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
  public void processTree() throws Exception {
    countHandler = new NameUsageMapperTreeTest.CountHandler<>();
    mapper().processTree(DATASET11.getKey(), null, "t2", Sets.newHashSet("skipID"), false, countHandler);
    Assert.assertEquals(19, countHandler.counter.get());
  
    countHandler.reset();
    mapper().processTree(DATASET11.getKey(), null,"t2", Sets.newHashSet("t6"), false, countHandler);
    Assert.assertEquals(13, countHandler.counter.get());
  
    countHandler.reset();
    mapper().processTree(DATASET11.getKey(), null,"t2", Sets.newHashSet("t6", "t30"), false, countHandler);
    Assert.assertEquals(8, countHandler.counter.get());
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
  
  @Test
  public void processTreeOrder() throws Exception {
    NameUsageMapperTreeTest.CollectIdHandler h = new NameUsageMapperTreeTest.CollectIdHandler();
    mapper().processTree(DATASET11.getKey(), null,null, null, false, h);
    List<String> bfs = ImmutableList.of("t1","t2","t3","t4","t5","t6","t10","t20","t30","t12","t13","t23","t24","t25","t31","t32","t33","t34","t15","t16");
    assertEquals(bfs, h.list);
  
    h = new NameUsageMapperTreeTest.CollectIdHandler();
    mapper().processTree(DATASET11.getKey(), null,null, null, true, h);
    List<String> dfs = ImmutableList.of("t1","t2","t3","t4","t5","t20","t23","t24","t25","t30","t31","t32","t33","t34","t6","t10","t12","t13","t15","t16");
    assertEquals(dfs, h.list);
  }
  
}
