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
    mapper().processDatasetUsages(NAME4.getDatasetKey(), new ResultHandler<NameUsageWrapper>() {
      public void handleResult(ResultContext<? extends NameUsageWrapper> ctx) {
        counter.incrementAndGet();
        NameUsageWrapper obj = ctx.getResultObject();
        assertNotNull(obj.getUsage().getId());
        if (obj.getUsage().getId().equals("root-1")) {
          assertEquals(4, obj.getIssues().size());
        } else {
          assertTrue(obj.getIssues().isEmpty());
        }

        Name n = obj.getUsage().getName();
        assertNotNull(n);
        assertNotNull(n.getId());
        assertNotNull(n.getDatasetKey());

        System.out.println(obj.getUsage().getId());
        if (obj.getUsage().getId().startsWith("root")) {
          assertTrue(obj.getUsage().isTaxon());
          Taxon t = (Taxon) obj.getUsage();
          System.out.println(t.getParentId());
        } else {
          assertTrue(obj.getUsage().isSynonym());
          Synonym s = (Synonym) obj.getUsage();
          assertNotNull(s.getParentId());
          System.out.println(s.getParentId());
        }
        System.out.println(ctx.getResultObject().getClassification());

        for (VernacularName v : ctx.getResultObject().getVernacularNames()) {
          assertNotNull(v.getName());
        }
      }
    });
    assertEquals(4, counter.get());
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
