package org.col.db.mapper;

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


public class NameUsageWrapperMapperTest extends MapperTestBase<NameUsageWrapperMapper> {
  
  public NameUsageWrapperMapperTest() {
    super(NameUsageWrapperMapper.class);
  }
  
  private AtomicInteger counter = new AtomicInteger(0);
  
  @Test
  public void processDatasetTaxa() throws Exception {
    mapper().processDatasetTaxa(NAME4.getDatasetKey(), null,new ResultHandler<NameUsageWrapper>() {
      public void handleResult(ResultContext<? extends NameUsageWrapper> ctx) {
        counter.incrementAndGet();
        NameUsageWrapper obj = ctx.getResultObject();
        if (obj.getUsage().getId().equals("root-1")) {
          assertEquals(4, obj.getIssues().size());
        } else {
          assertNull(obj.getIssues());
        }

        Name n = obj.getUsage().getName();
        assertNotNull(n);
        assertNotNull(n.getId());
        assertNotNull(n.getDatasetKey());

        assertTrue(obj.getUsage().isTaxon());
        Taxon t = (Taxon) obj.getUsage();
        assertNotNull(t.getId());
        System.out.println(t.getId());
        System.out.println(t.getParentId());
        System.out.println(ctx.getResultObject().getClassification());

        for (VernacularName v : ctx.getResultObject().getVernacularNames()) {
          assertNotNull(v.getName());
        }
      }
    });
    Assert.assertEquals(2, counter.get());
  }
  
  @Test
  public void processDatasetSynonyms() throws Exception {
    mapper().processDatasetSynonyms(NAME4.getDatasetKey(), null,new ResultHandler<NameUsageWrapper>() {
      public void handleResult(ResultContext<? extends NameUsageWrapper> ctx) {
        counter.incrementAndGet();
        assertTrue(ctx.getResultObject().getUsage().getStatus().isSynonym());
        assertTrue(ctx.getResultObject().getUsage().isSynonym());
        Synonym s = (Synonym) ctx.getResultObject().getUsage();
        assertNotNull(s.getAccepted());
      }
    });
    Assert.assertEquals(2, counter.get());
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
    Assert.assertEquals(1, counter.get());
  }
}
