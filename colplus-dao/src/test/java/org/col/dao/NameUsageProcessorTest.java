package org.col.dao;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.col.api.model.Name;
import org.col.api.model.SimpleName;
import org.col.api.model.Taxon;
import org.col.api.model.VernacularName;
import org.col.api.search.NameUsageWrapper;
import org.col.api.vocab.Datasets;
import org.col.db.MybatisTestUtils;
import org.col.db.PgSetupRule;
import org.col.db.mapper.TestDataRule;
import org.junit.Assert;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.NAME4;
import static org.junit.Assert.*;

public class NameUsageProcessorTest extends DaoTestBase {
  
  public NameUsageProcessorTest() {
    super(TestDataRule.tree());
  }
  
  @Test
  public void processDataset() {
    DRH handler = new DRH();
    NameUsageProcessor proc = new NameUsageProcessor(PgSetupRule.getSqlSessionFactory());
    proc.processDataset(NAME4.getDatasetKey(), handler);
    Assert.assertEquals(24, handler.counter.get());
    Assert.assertEquals(4, handler.synCounter.get());
  }
  
  public static class DRH implements Consumer<NameUsageWrapper> {
    public AtomicInteger counter = new AtomicInteger(0);
    public AtomicInteger synCounter = new AtomicInteger(0);
    
    @Override
    public void accept(NameUsageWrapper obj) {
      counter.incrementAndGet();
      assertNotNull(obj.getUsage().getId());

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
        for (VernacularName v : obj.getVernacularNames()) {
          assertNotNull(v.getName());
        }
        
      } else {
        assertTrue(obj.getUsage().isSynonym());
        synCounter.incrementAndGet();
      }
    }
  }
  
  @Test
  public void processSector() throws Exception {
    MybatisTestUtils.populateDraftTree(session());
    AtomicInteger counter = new AtomicInteger(0);
  
    NameUsageProcessor proc = new NameUsageProcessor(PgSetupRule.getSqlSessionFactory());
    proc.processSector(Datasets.DRAFT_COL, 1, "t2", new Consumer<NameUsageWrapper>() {
      public void accept(NameUsageWrapper obj) {
        counter.incrementAndGet();
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
}