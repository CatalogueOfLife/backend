package org.col.db.mapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.model.*;
import org.col.api.search.NameUsageWrapper;
import org.col.api.vocab.Datasets;
import org.col.db.MybatisTestUtils;
import org.junit.Assert;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.NAME4;
import static org.junit.Assert.*;


public class NameUsageWrapperMapperTreeTest extends MapperTestBase<NameUsageWrapperMapper> {
  
  public NameUsageWrapperMapperTreeTest() {
    super(NameUsageWrapperMapper.class, TestDataRule.tree());
  }
  
  private AtomicInteger counter = new AtomicInteger(0);
  
  @Test
  public void getTaxa() throws Exception {
    
    List<?> cl = mapper(TaxonMapper.class).classificationSimple(NAME4.getDatasetKey(), "t15");
    assertEquals(7, cl.size());
    
    NameUsageWrapper tax = mapper().get(NAME4.getDatasetKey(), "t15");
    assertFalse(tax.getClassification().isEmpty());
    assertEquals(cl, tax.getClassification());
  }
  
  @Test
  public void processDatasetTaxa() throws Exception {
    AtomicInteger synCounter = new AtomicInteger(0);
    mapper().processDatasetUsages(NAME4.getDatasetKey(), new ResultHandler<NameUsageWrapper>() {
      public void handleResult(ResultContext<? extends NameUsageWrapper> ctx) {
        counter.incrementAndGet();
        NameUsageWrapper obj = ctx.getResultObject();
        Name n = obj.getUsage().getName();
        assertNotNull(n);
        assertNotNull(n.getId());
        assertNotNull(n.getDatasetKey());
        
        // classification should always include the taxon itself
        // https://github.com/Sp2000/colplus-backend/issues/326
        assertFalse(obj.getClassification().isEmpty());
        SimpleName last = obj.getClassification().get(obj.getClassification().size()-1);
        assertEquals(obj.getUsage().getId(), last.getId());
        
        if ( obj.getUsage().getId().startsWith("t")) {
          assertTrue(obj.getUsage().isTaxon());
          Taxon t = (Taxon) obj.getUsage();
          assertNotNull(t.getId());
          assertEquals((Integer) 1, t.getVerbatimKey());
          if (t.getId().equals("t1")) {
            assertNull(t.getParentId());
          } else {
            assertNotNull(t.getParentId());
          }
          for (VernacularName v : ctx.getResultObject().getVernacularNames()) {
            assertNotNull(v.getName());
          }

        } else {
          assertTrue(obj.getUsage().isSynonym());
          synCounter.incrementAndGet();
        }

      }
    });
    Assert.assertEquals(24, counter.get());
    Assert.assertEquals(4, synCounter.get());
  }
  
  @Test
  public void processSector() throws Exception {
    MybatisTestUtils.populateDraftTree(session());

    mapper().processSectorUsages(1, "t2", new ResultHandler<NameUsageWrapper>() {
      public void handleResult(ResultContext<? extends NameUsageWrapper> ctx) {
        counter.incrementAndGet();
        NameUsageWrapper obj = ctx.getResultObject();
        Name n = obj.getUsage().getName();
        assertNotNull(n);
        assertNotNull(n.getId());
        assertEquals(Datasets.DRAFT_COL, (int) obj.getUsage().getDatasetKey());
        assertEquals(Datasets.DRAFT_COL, (int) n.getDatasetKey());
        
        // classification should always include the taxon itself
        // https://github.com/Sp2000/colplus-backend/issues/326
        assertFalse(obj.getClassification().isEmpty());
        SimpleName last = obj.getClassification().get(obj.getClassification().size()-1);
        assertEquals(obj.getUsage().getId(), last.getId());
        
        // we have no sector, so we just get the root usage back
        assertEquals(obj.getUsage().getId(), last.getId());
      }
    });
    Assert.assertEquals(1, counter.get());
  }

  @Test
  public void processDatasetBareNames() throws Exception {
    mapper().processDatasetBareNames(NAME4.getDatasetKey(), null,new ResultHandler<NameUsageWrapper>() {
      public void handleResult(ResultContext<? extends NameUsageWrapper> ctx) {
        counter.incrementAndGet();
        assertNotNull(ctx.getResultObject());
        assertNotNull(ctx.getResultObject().getUsage());
        assertNotNull(ctx.getResultObject().getUsage().getName());
      }
    });
    Assert.assertEquals(0, counter.get());
  }
  
  @Test
  public void processSubtree() throws Exception {
    mapper().processTree(NAME4.getDatasetKey(), "t4",new ResultHandler<SimpleNameClassification>() {
      public void handleResult(ResultContext<? extends SimpleNameClassification> ctx) {
        counter.incrementAndGet();
        SimpleNameClassification obj = ctx.getResultObject();
        assertNotNull(obj.getClassification());
        
        // classification should always include the taxon itself
        // https://github.com/Sp2000/colplus-backend/issues/326
        assertFalse(obj.getClassification().isEmpty());
        SimpleName last = obj.getClassification().get(obj.getClassification().size()-1);
        assertEquals(obj.getId(), last.getId());
        
        // classification should always start with the root of the dataset, not the root of the traversal!
        assertEquals("t1", obj.getClassification().get(0).getId());
        assertEquals("t2", obj.getClassification().get(1).getId());
        assertEquals("t3", obj.getClassification().get(2).getId());
      }
    });
    Assert.assertEquals(21, counter.get());
  }
}
