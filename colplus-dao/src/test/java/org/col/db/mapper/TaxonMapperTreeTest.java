package org.col.db.mapper;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.collect.Sets;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.model.Taxon;
import org.junit.Assert;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.DATASET11;
import static org.junit.Assert.assertTrue;


public class TaxonMapperTreeTest extends MapperTestBase<TaxonMapper> {
  
  CountHandler countHandler;
  
  public TaxonMapperTreeTest() {
    super(TaxonMapper.class, InitMybatisRule.tree());
  }
  
  
  @Test
  public void processTree() throws Exception {
    countHandler = new CountHandler();
    mapper().processTree(DATASET11.getKey(), "t2", Sets.newHashSet("skipID"), countHandler);
    Assert.assertEquals(19, countHandler.counter.get());
  
    countHandler.reset();
    mapper().processTree(DATASET11.getKey(), "t2", Sets.newHashSet("t6"), countHandler);
    Assert.assertEquals(13, countHandler.counter.get());
  
    countHandler.reset();
    mapper().processTree(DATASET11.getKey(), "t2", Sets.newHashSet("t6", "t30"), countHandler);
    Assert.assertEquals(8, countHandler.counter.get());
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
  
  
}
