package life.catalogue.db.mapper;

import life.catalogue.api.model.NameUsage;
import life.catalogue.api.model.NameUsageBase;
import life.catalogue.db.TestDataRule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import static life.catalogue.api.TestEntityGenerator.DATASET11;
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
    mapper().processTree(DATASET11.getKey(), null, "t2", Sets.newHashSet("skipID"), null, true, false)
            .forEach(countHandler);
    Assert.assertEquals(23, countHandler.counter.get());
  
    countHandler.reset();
    mapper().processTree(DATASET11.getKey(), null,"t2", Sets.newHashSet("t6"), null, true, false)
            .forEach(countHandler);
    Assert.assertEquals(15, countHandler.counter.get());
  
    countHandler.reset();
    mapper().processTree(DATASET11.getKey(), null,"t2", Sets.newHashSet("t6", "t30"), null, true, false)
            .forEach(countHandler);
    Assert.assertEquals(10, countHandler.counter.get());
  }
  
  @Test
  public void processTreeOrder() throws Exception {
    CollectIdHandler<NameUsageBase> h = new CollectIdHandler<>();
    mapper().processTree(DATASET11.getKey(), null,null, null, null, true,false)
            .forEach(h);
    List<String> bfs = ImmutableList.of("t1","t2","t3","t4",
      "t5","t6","t30",
      "t10","t20", "t31","t32","t33","t34",
      "s11", "t12","t13", "s21", "s22","t23","t24","t25",
      "s14", "t15", "t16"
    );
    assertEquals(bfs, h.list);
  
    h = new CollectIdHandler<>();
    mapper().processTree(DATASET11.getKey(), null,null, null, null, true, true)
            .forEach(h);
    List<String> dfs = ImmutableList.of("t1","t2","t3","t4","t5",
        "t20","s21","s22","t23","t24","t25",
        "t6","t10","s11","t12","t13","s14","t15","t16",
        "t30","t31","t32","t33","t34"
    );
    assertEquals(dfs, h.list);
  }
  
  public static class CountHandler<T extends NameUsageBase> implements Consumer<T> {
    public AtomicInteger counter = new AtomicInteger(0);
    Set<String> previous = new HashSet<>();
    
    public void accept(T u) {
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
  
  public static class CollectIdHandler<T extends NameUsage> implements Consumer<T> {
    public List<String> list = new ArrayList<>();
    
    public void accept(T t) {
      list.add(t.getId());
    }
  }
  
}
