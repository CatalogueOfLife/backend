package org.col.db.mapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.model.Taxon;
import org.col.api.model.TaxonCountMap;
import org.junit.Assert;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.DATASET11;
import static org.col.api.TestEntityGenerator.NAME4;
import static org.junit.Assert.*;


public class TaxonMapperTreeTest extends MapperTestBase<TaxonMapper> {
  
  CountHandler countHandler;
  
  public TaxonMapperTreeTest() {
    super(TaxonMapper.class, TestDataRule.tree());
  }
  
  
  @Test
  public void processTree() throws Exception {
    countHandler = new CountHandler();
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
      assertNull(c.getCount());
    }
  }
  
  @Test
  public void classificationSimple() throws Exception {
    List<?> cl = mapper().classificationSimple(NAME4.getDatasetKey(), "t15");
    assertEquals(7, cl.size());
  }
  
  @Test
  public void processTreeOrder() throws Exception {
    CollectIdHandler h = new CollectIdHandler();
    mapper().processTree(DATASET11.getKey(), null,null, null, false, h);
    List<String> bfs = ImmutableList.of("t1","t2","t3","t4","t5","t6","t10","t20","t30","t12","t13","t23","t24","t25","t31","t32","t33","t34","t15","t16");
    assertEquals(bfs, h.list);
  
    h = new CollectIdHandler();
    mapper().processTree(DATASET11.getKey(), null,null, null, true, h);
    List<String> dfs = ImmutableList.of("t1","t2","t3","t4","t5","t20","t23","t24","t25","t30","t31","t32","t33","t34","t6","t10","t12","t13","t15","t16");
    assertEquals(dfs, h.list);
  }
  
  static class CountHandler implements ResultHandler<Taxon> {
    public AtomicInteger counter = new AtomicInteger(0);
    Set<String> previous = new HashSet<>();
    
    public void handleResult(ResultContext<? extends Taxon> ctx) {
      Taxon t = ctx.getResultObject();
      assertTrue(counter.get()==0 || previous.contains(t.getParentId()));

      System.out.println(t.getId());
      previous.add(t.getId());
      counter.incrementAndGet();
    }
    
    public void reset() {
      counter.set(0);
      previous.clear();
    }
  }
  
  static class CollectIdHandler implements ResultHandler<Taxon> {
    public List<String> list = new ArrayList<>();
    
    public void handleResult(ResultContext<? extends Taxon> ctx) {
      Taxon t = ctx.getResultObject();
      list.add(t.getId());
    }
  }
  
}
