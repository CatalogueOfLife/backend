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
import org.col.api.model.NameUsage;
import org.col.api.model.NameUsageBase;
import org.junit.Assert;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.DATASET11;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class NameUsageMapperTreeTest extends MapperTestBase<NameUsageMapper> {
  
  CountHandler<NameUsageBase> countHandler;
  
  public NameUsageMapperTreeTest() {
    super(NameUsageMapper.class, TestDataRule.tree());
  }
  
  
  @Test
  public void processTree() throws Exception {
    countHandler = new CountHandler<>();
    mapper().processTree(DATASET11.getKey(), null, "t2", Sets.newHashSet("skipID"), true, false, countHandler);
    Assert.assertEquals(23, countHandler.counter.get());
  
    countHandler.reset();
    mapper().processTree(DATASET11.getKey(), null,"t2", Sets.newHashSet("t6"), true, false, countHandler);
    Assert.assertEquals(15, countHandler.counter.get());
  
    countHandler.reset();
    mapper().processTree(DATASET11.getKey(), null,"t2", Sets.newHashSet("t6", "t30"), true, false, countHandler);
    Assert.assertEquals(10, countHandler.counter.get());
  }
  
  @Test
  public void processTreeOrder() throws Exception {
    CollectIdHandler<NameUsageBase> h = new CollectIdHandler<>();
    mapper().processTree(DATASET11.getKey(), null,null, null, true, false, h);
    List<String> bfs = ImmutableList.of("t1","t2","t3","t4","t5","t6","t10","t20","t30","t12","t13","t23","t24","t25",
        "t31","t32","t33","t34", "s11", "s21", "s22", "t15", "t16", "s14");
    assertEquals(bfs, h.list);
  
    h = new CollectIdHandler<>();
    mapper().processTree(DATASET11.getKey(), null,null, null, true, true, h);
    List<String> dfs = ImmutableList.of("t1","t2","t3","t4","t5","t20","s21","s22","t23","t24","t25",
        "t30","t31","t32","t33","t34","t6","t10","s11","t12","t13","s14","t15","t16");
    assertEquals(dfs, h.list);
  }
  
  
  
  public static class CountHandler<T extends NameUsageBase> implements ResultHandler<T> {
    public AtomicInteger counter = new AtomicInteger(0);
    Set<String> previous = new HashSet<>();
    
    public void handleResult(ResultContext<? extends T> ctx) {
      T u = ctx.getResultObject();
      assertTrue(counter.get()==0 || previous.contains(u.getParentId()));

      System.out.println(u.getId());
      previous.add(u.getId());
      counter.incrementAndGet();
    }
    
    public void reset() {
      counter.set(0);
      previous.clear();
    }
  }
  
  public static class CollectIdHandler<T extends NameUsage> implements ResultHandler<T> {
    public List<String> list = new ArrayList<>();
    
    public void handleResult(ResultContext<? extends T> ctx) {
      T t = ctx.getResultObject();
      list.add(t.getId());
    }
  }
  
}
