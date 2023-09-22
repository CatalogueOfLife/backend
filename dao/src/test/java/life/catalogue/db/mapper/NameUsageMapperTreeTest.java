package life.catalogue.db.mapper;

import life.catalogue.api.model.*;
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
import static org.junit.Assert.*;


public class NameUsageMapperTreeTest extends MapperTestBase<NameUsageMapper> {
  
  CountHandler countHandler;
  
  public NameUsageMapperTreeTest() {
    super(NameUsageMapper.class, TestDataRule.tree());
  }

  @Test
  public void processTree() throws Exception {
    countHandler = new CountHandler();
    var ttp = TreeTraversalParameter.dataset(DATASET11.getKey());
    ttp.setTaxonID("t2");
    ttp.setExclusion(Sets.newHashSet("skipID"));
    ttp.setSynonyms(true);
    mapper().processTree(ttp, false)
            .forEach(countHandler);
    Assert.assertEquals(23, countHandler.counter.get());
  
    countHandler.reset();
    ttp.setExclusion(Sets.newHashSet("t6"));
    mapper().processTree(ttp, false)
            .forEach(countHandler);
    Assert.assertEquals(15, countHandler.counter.get());
  
    countHandler.reset();
    ttp.setExclusion(Sets.newHashSet("t6", "t30"));
    mapper().processTree(ttp, false)
            .forEach(countHandler);
    Assert.assertEquals(10, countHandler.counter.get());
  }

  @Test
  public void processTreeSimpleUsage() throws Exception {
    countHandler = new CountHandler<SimpleNameUsage>(u -> {
      assertNotNull(u.getName());
    });
    var ttp = TreeTraversalParameter.dataset(DATASET11.getKey());
    ttp.setTaxonID("t2");
    ttp.setExclusion(Sets.newHashSet("skipID"));
    ttp.setSynonyms(true);
    mapper().processTreeSimpleUsage(ttp).forEach(countHandler);
    Assert.assertEquals(23, countHandler.counter.get());

    countHandler.reset();
    ttp.setExclusion(Sets.newHashSet("t6"));
    mapper().processTreeSimpleUsage(ttp).forEach(countHandler);
    Assert.assertEquals(15, countHandler.counter.get());

    countHandler.reset();
    ttp.setExclusion(Sets.newHashSet("t6", "t30"));
    mapper().processTreeSimpleUsage(ttp).forEach(countHandler);
    Assert.assertEquals(10, countHandler.counter.get());
  }

  @Test
  public void processTreeOrder() throws Exception {
    CollectIdHandler<NameUsageBase> h = new CollectIdHandler<>();
    mapper().processTree(TreeTraversalParameter.dataset(DATASET11.getKey()),false)
            .forEach(h);
    List<String> bfs = ImmutableList.of("t1","t2","t3","t4",
      "t5","t6","t30",
      "t31","t32","t33","t34", "t20","t10",
      "t12","t13", "s11", "t23","t24","t25", "s21","s22",
      "t15", "t16", "s14"
      );
    assertEquals(bfs, h.list);
  
    h = new CollectIdHandler<>();
    mapper().processTree(TreeTraversalParameter.dataset(DATASET11.getKey()), true)
            .forEach(h);
    List<String> dfs = ImmutableList.of("t1","t2","t3","t4","t5",
        "t20","s21","s22","t23","t24","t25",
        "t6","t10","s11","t12","t13","s14","t15","t16",
        "t30","t31","t32","t33","t34"
    );
    assertEquals(dfs, h.list);
  }
  
  public static class CountHandler<T extends NameUsageCore> implements Consumer<T> {
    public AtomicInteger counter = new AtomicInteger(0);
    Set<String> previous = new HashSet<>();
    final Consumer<T> consumer;

    public CountHandler() {
      this(null);
    }

    public CountHandler(Consumer<T> consumer) {
      this.consumer = consumer;
    }

    public void accept(T u) {
      assertNotNull(u.getId());
      assertTrue(counter.get()==0 || previous.contains(u.getParentId()));
      if (consumer != null) {
        consumer.accept(u);
      }

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
