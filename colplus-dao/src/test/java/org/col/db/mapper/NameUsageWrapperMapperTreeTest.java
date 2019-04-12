package org.col.db.mapper;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.model.Name;
import org.col.api.model.Taxon;
import org.col.api.model.VernacularName;
import org.col.api.search.NameUsageWrapper;
import org.junit.Assert;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.NAME4;
import static org.junit.Assert.*;


public class NameUsageWrapperMapperTreeTest extends MapperTestBase<NameUsageWrapperMapper> {
  
  public NameUsageWrapperMapperTreeTest() {
    super(NameUsageWrapperMapper.class, InitMybatisRule.tree());
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
    mapper().processDatasetUsages(NAME4.getDatasetKey(), null,new ResultHandler<NameUsageWrapper>() {
      public void handleResult(ResultContext<? extends NameUsageWrapper> ctx) {
        counter.incrementAndGet();
        NameUsageWrapper obj = ctx.getResultObject();
        Name n = obj.getUsage().getName();
        assertNotNull(n);
        assertNotNull(n.getId());
        assertNotNull(n.getDatasetKey());

        if ( obj.getUsage().getId().startsWith("t")) {
          assertTrue(obj.getUsage().isTaxon());
          Taxon t = (Taxon) obj.getUsage();
          assertNotNull(t.getId());
          assertNotNull(t.getAccordingToDate());
          assertEquals("M.Döring", t.getAccordingTo());
          assertEquals((Integer) 10, t.getSpeciesEstimate());
          assertEquals((Integer) 1, t.getVerbatimKey());
          assertEquals("remark me", t.getRemarks());
          assertEquals(URI.create("http://myspace.com"), t.getWebpage());
          if (t.getId().equals("t1")) {
            assertNull(t.getParentId());
            assertTrue(obj.getClassification().isEmpty());
          } else {
            assertNotNull(t.getParentId());
            assertFalse(obj.getClassification().isEmpty());
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
}
