package org.col.db.mapper;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.model.Name;
import org.col.api.model.Synonym;
import org.col.api.model.Taxon;
import org.col.api.model.VernacularName;
import org.col.api.search.NameUsageWrapper;
import org.junit.Assert;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.NAME4;
import static org.junit.Assert.*;


public class NameUsageMapperTreeTest extends MapperTestBase<NameUsageMapper> {
  
  public NameUsageMapperTreeTest() {
    super(NameUsageMapper.class, InitMybatisRule.tree());
  }
  
  private AtomicInteger counter = new AtomicInteger(0);
  
  @Test
  public void processDatasetTaxa() throws Exception {
    mapper().processDatasetTaxa(NAME4.getDatasetKey(), new ResultHandler<NameUsageWrapper>() {
      public void handleResult(ResultContext<? extends NameUsageWrapper> ctx) {
        counter.incrementAndGet();
        NameUsageWrapper obj = ctx.getResultObject();
        Name n = obj.getUsage().getName();
        assertNotNull(n);
        assertNotNull(n.getId());
        assertNotNull(n.getDatasetKey());

        assertTrue(obj.getUsage().isTaxon());
        Taxon t = (Taxon) obj.getUsage();
        assertNotNull(t.getId());
        assertNotNull(t.getAccordingToDate());
        assertEquals("M.Döring", t.getAccordingTo());
        assertEquals((Integer) 10, t.getSpeciesEstimate());
        assertEquals((Integer) 1, t.getVerbatimKey());
        assertEquals("remark me", t.getRemarks());
        assertEquals(URI.create("http://myspace.com"), t.getDatasetUrl());
        if (t.getId().equals("t1")) {
          assertNull(t.getParentId());
          // Not true any longer; see NameUsafeTransfer.saveClassification
          //assertTrue(obj.getClassification().isEmpty());
          assertEquals(1,obj.getClassification().size());
        } else {
          assertNotNull(t.getParentId());
          assertFalse(obj.getClassification().isEmpty());
        }

        for (VernacularName v : ctx.getResultObject().getVernacularNames()) {
          assertNotNull(v.getName());
        }
      }
    });
    Assert.assertEquals(20, counter.get());
  }
  
  @Test
  public void processDatasetSynonyms() throws Exception {
    mapper().processDatasetSynonyms(NAME4.getDatasetKey(), new ResultHandler<NameUsageWrapper>() {
      public void handleResult(ResultContext<? extends NameUsageWrapper> ctx) {
        counter.incrementAndGet();
        assertTrue(ctx.getResultObject().getUsage().getStatus().isSynonym());
        assertTrue(ctx.getResultObject().getUsage().isSynonym());
        Synonym s = (Synonym) ctx.getResultObject().getUsage();
        assertNotNull(s.getAccepted());
        assertEquals("M.Döring", s.getAccordingTo());
        assertEquals((Integer) 1, s.getVerbatimKey());
      }
    });
    Assert.assertEquals(4, counter.get());
  }
  
  @Test
  public void processDatasetBareNames() throws Exception {
    mapper().processDatasetBareNames(NAME4.getDatasetKey(), new ResultHandler<NameUsageWrapper>() {
      public void handleResult(ResultContext<? extends NameUsageWrapper> ctx) {
        counter.incrementAndGet();
        assertNotNull(ctx.getResultObject());
        assertNotNull(ctx.getResultObject().getUsage());
        assertNotNull(ctx.getResultObject().getUsage().getName());
      }
    });
    Assert.assertEquals(0, counter.get());
  }
}
